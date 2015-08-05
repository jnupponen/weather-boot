package com.myrskytin.weatherboot;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.spring.boot.FatJarRouter;
import org.apache.camel.util.URISupport;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.net.URLCodec;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WeatherBootRoute extends FatJarRouter {
	private static final Logger LOG = Logger.getLogger(WeatherBootRoute.class);

	private final String port;
	private final String getUrl;
	private final String name;
	private final String imageUrl;
	private final String slackUrl;

	private static final List<String> MUNICIPALITIES = Municipalities.get();

	public WeatherBootRoute() {

		this.port = (System.getenv("PORT") != null ? System.getenv("PORT") : "8181");
		this.name = (System.getenv("WEATHER_BOOT_NAME") != null ? System.getenv("WEATHER_BOOT_NAME") : "WeatherBoot");
		this.getUrl = (System.getenv("WEATHER_BOOT_SOURCE") != null ? System.getenv("WEATHER_BOOT_SOURCE") : "localhost/");
		this.imageUrl = (System.getenv("WEATHER_BOOT_IMAGE") != null ? System.getenv("WEATHER_BOOT_IMAGE") : "");
		this.slackUrl = (System.getenv("WEATHER_BOOT_TARGET") != null ? System.getenv("WEATHER_BOOT_TARGET") : "localhost");
	}

	@Override
	public void configure() {

		from("netty-http://http://0.0.0.0:" + port + "/weatherhook")
			.routeId("weather-hook-route")
			.wireTap("direct:tap")
			.transform().constant("ok");
		
		from("direct:tap")
			.process(exchange -> {
				InputStream stream = (InputStream) exchange.getIn().getBody();
				byte[] array = IOUtils.toByteArray(stream);
				String decoded = new String(array, "UTF-8");
				
				Map<String, Object> data = URISupport.parseQuery(decoded);
				LOG.info("Decoded:"+data.toString());
				String name = checkName(data);
				LOG.info("Name: "+name);
				exchange.getOut().setHeader("municipality", name);
			})
			.setBody(constant(null))
			.log("Making request to: "+getUrl+"${in.header.municipality}")
			.recipientList(simple("http4://"+getUrl+"${in.header.municipality}"))
			.convertBodyTo(String.class)
			.process(
				exchange -> {
					Document doc = Jsoup.parse((String) exchange.getIn().getBody());
					String temperature = doc.select("tr.meteogram-temperatures").first().select("td").first()
						.select("div").first().text().toString();

					exchange.getOut().setHeader("temperature", temperature);
				})
			.log("Temperature [${header.temperature}].")
			.process(
				exchange -> {
					String body = "payload={\"text\": \"" + exchange.getIn().getHeader("temperature", String.class)
						+ "\", \"channel\": \"#general\", \"username\": \""+name+"\", \"icon_url\":"
						+ " \""+imageUrl+"\"}";
					exchange.getOut().setBody(body);
					exchange.getOut().setHeader(Exchange.CONTENT_TYPE, "application/x-www-form-urlencoded");
					exchange.getOut().setHeader(Exchange.HTTP_CHARACTER_ENCODING, "UTF-8");
				})
			.to("https4://"+slackUrl)
			.log("Slack responded: [${header." + Exchange.HTTP_RESPONSE_CODE + "}]")
			.process(
				exchange -> {
					String temperature = exchange.getIn().getHeader("temperature", String.class);
					String html = "<html><head><meta name=\"description\" content=\"" + temperature
						+ "\"/></head><body>" + temperature + "</body></html>";

					exchange.getOut().setBody(html);
					exchange.getOut().setHeader("CamelHttpCharacterEncoding", "UTF-8");
					exchange.getOut().setHeader("Content-Type", "text/html; charset=UTF-8");
				})
			.log("Done.");
	}
	
	private String checkName(Map<String, Object> data) {
		if(data.containsKey("text")) {
			URLCodec codec = new URLCodec();

			String value = (String) data.get("text");
			for(String word : Arrays.asList(value.split(" "))) {
				try {
					String decoded = codec.decode(URISupport.sanitizeUri(word));

					if(MUNICIPALITIES.contains(Municipalities.toSimpleName(decoded))) {
						return Municipalities.toSimpleName(decoded);
					}
				} catch (DecoderException e) {
					LOG.error("Some crap in the word. Exception was: "+e);
				}
				
			}
		}
		return "tampere";
	}
}

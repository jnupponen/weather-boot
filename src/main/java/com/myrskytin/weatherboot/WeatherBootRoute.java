package com.myrskytin.weatherboot;

import org.apache.camel.Exchange;
import org.apache.camel.spring.boot.FatJarRouter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WeatherBootRoute extends FatJarRouter {

	private final String port;
	private final String getUrl;
	private final String name;
	private final String imageUrl;
	private final String slackUrl;

	public WeatherBootRoute() {

		this.port = (System.getenv("PORT") != null ? System.getenv("PORT") : "8181");
		this.name = (System.getenv("WEATHER_BOOT_NAME") != null ? System.getenv("WEATHER_BOOT_NAME") : "WeatherBoot");
		this.getUrl = (System.getenv("WEATHER_BOOT_SOURCE") != null ? System.getenv("WEATHER_BOOT_SOURCE") : "localhost");
		this.imageUrl = (System.getenv("WEATHER_BOOT_IMAGE") != null ? System.getenv("WEATHER_BOOT_IMAGE") : "");
		this.slackUrl = (System.getenv("WEATHER_BOOT_TARGET") != null ? System.getenv("WEATHER_BOOT_TARGET") : "localhost");
	}

	@Override
	public void configure() {

		from("netty-http://http://0.0.0.0:" + port + "/weatherhook")
			.routeId("weather-hook-route")
			.process(exchange -> exchange.getOut().setBody(null))
			.to("http4://"+getUrl)
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
}

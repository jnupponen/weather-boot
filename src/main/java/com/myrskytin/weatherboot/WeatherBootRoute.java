package com.myrskytin.weatherboot;

import javax.json.Json;
import javax.json.JsonObject;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.spring.boot.FatJarRouter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.rendersnake.HtmlAttributes;
import org.rendersnake.HtmlCanvas;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WeatherBootRoute extends FatJarRouter {

	@Override
	public void configure() {

		from("netty-http://http://0.0.0.0:8181").process(new Processor() {

			@Override
			public void process(Exchange exchange) throws Exception {
				exchange.getOut().setBody(null);

			}
		}).to("http4://ilmatieteenlaitos.fi/saa/tampere")
				.convertBodyTo(String.class).process(new Processor() {

					@Override
					public void process(Exchange exchange) throws Exception {
						Document doc = Jsoup.parse((String) exchange.getIn()
								.getBody());
						String temperature = doc
								.select("tr.meteogram-temperatures").first()
								.select("td").first().select("div").first()
								.text().toString();
						
						String html = "<html><head><meta name=\"description\" content=\""+temperature+"\"/></head><body>"+temperature+"</body></html>"; 
						
						exchange.getOut().setBody(html);
						exchange.getOut().setHeader(
								"CamelHttpCharacterEncoding", "UTF-8");
						exchange.getOut().setHeader("Content-Type",
								"text/html; charset=UTF-8");

					}
				}).to("log:temperature");
	}
}

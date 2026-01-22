package util;

import javax.xml.parsers.*;
import javax.xml.xpath.*;
import org.w3c.dom.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ParseXML {

	public static List<String> parseXml(String blocXml) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = factory.newDocumentBuilder();
		Document doc = builder.parse(new ByteArrayInputStream(blocXml.getBytes(StandardCharsets.UTF_8)));
		XPath xpath = XPathFactory.newInstance().newXPath();

		List<String> result = new ArrayList<>();
		NodeList nodes = (NodeList) xpath.evaluate("//text()", doc, XPathConstants.NODESET);

		for (int i = 0; i < nodes.getLength(); i++) {
			String text = nodes.item(i).getTextContent();
			if (!text.trim().isEmpty()) { // If it has non-whitespace content
				result.add(text);
			}
		}

		return result;
	}

	public static String trimAndLowerCaseAndRemoveLineBreaks(String input) {
		if (input == null) {
			return null;
		}
		// Remove line breaks and trim whitespace
		return input
				.replace('\u00A0', ' ')
				.replaceAll("\\r?\\n", " ")
				.trim()
				.toLowerCase();
	}
}

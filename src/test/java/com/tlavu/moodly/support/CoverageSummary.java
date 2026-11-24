package com.tlavu.moodly.support;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Element;

final class CoverageSummary {
	private CoverageSummary() {}

	static void run() throws Exception {
		Path reportPath = Path.of("target/site/jacoco/jacoco.xml");
		if (!Files.isRegularFile(reportPath)) {
			return;
		}
		var factory = DocumentBuilderFactory.newInstance();
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
		factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		var builder = factory.newDocumentBuilder();
		var pom = builder.parse(Path.of("pom.xml").toFile());
		var report = builder.parse(reportPath.toFile());
		var xpath = XPathFactory.newInstance().newXPath();
		String rule = "/project/build/plugins/plugin[artifactId='jacoco-maven-plugin']/executions/"
				+ "execution[id='enforce-coverage-baseline']/configuration/rules/rule[element='BUNDLE']/limits/";
		var summary = new StringBuilder("## JaCoCo coverage\n\n")
				.append("| Metric | Covered | Total | Coverage | Minimum | Result |\n")
				.append("| --- | ---: | ---: | ---: | ---: | --- |\n");
		for (String name : new String[]{"LINE", "BRANCH", "METHOD", "INSTRUCTION", "CLASS"}) {
			var entry = (Element) xpath.evaluate("/report/counter[@type='" + name + "']", report, XPathConstants.NODE);
			long covered = entry == null ? 0 : Long.parseLong(entry.getAttribute("covered"));
			long total = covered + (entry == null ? 0 : Long.parseLong(entry.getAttribute("missed")));
			var minimum = new BigDecimal(xpath.evaluate(
					rule + "limit[counter='" + name + "' and value='COVEREDRATIO']/minimum", pom));
			String result = total == 0 ? "—" : BigDecimal.valueOf(covered)
					.compareTo(minimum.multiply(BigDecimal.valueOf(total))) >= 0 ? "Pass" : "Fail";
			summary.append(String.format(Locale.ROOT, "| %s | %d | %d | %s | %.2f%% | %s |%n",
					name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT), covered, total,
					total == 0 ? "—" : String.format(Locale.ROOT, "%.2f%%", 100.0 * covered / total),
					minimum.movePointRight(2), result));
		}
		System.out.print(summary);
		AutomationTooling.appendSummary(summary.toString());
	}
}

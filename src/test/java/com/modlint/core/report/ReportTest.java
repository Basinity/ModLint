package com.modlint.core.report;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.modlint.core.analysis.Finding;
import com.modlint.core.analysis.Severity;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportTest {

    @Test
    void jsonShapeIsStable() {
        Report report = new Report(2, 1, "fabric", "1.20.1", List.of(
                new Finding("missing-dependency", Severity.HIGH, List.of("iris", "sodium"),
                        "Iris requires sodium [0.5.x], but no installed mod provides it.",
                        "Install sodium in a version matching [0.5.x].")));

        String expected = """
                {
                  "jars": 2,
                  "mods": 1,
                  "loader": "fabric",
                  "minecraftVersion": "1.20.1",
                  "findings": [
                    {
                      "type": "missing-dependency",
                      "severity": "HIGH",
                      "mods": [
                        "iris",
                        "sodium"
                      ],
                      "problem": "Iris requires sodium [0.5.x], but no installed mod provides it.",
                      "fix": "Install sodium in a version matching [0.5.x]."
                    }
                  ]
                }""";
        assertEquals(expected, report.toJson());
    }

    @Test
    void absentMinecraftVersionIsOmitted() {
        Report report = new Report(0, 0, "forge", null, List.of());
        assertEquals("""
                {
                  "jars": 0,
                  "mods": 0,
                  "loader": "forge",
                  "findings": []
                }""", report.toJson());
    }
}

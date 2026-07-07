export type Severity = "HIGH" | "MEDIUM" | "LOW" | "POTENTIAL";

export interface Finding {
  type: string;
  severity: Severity;
  mods: string[];
  problem: string;
  fix: string;
}

export interface Report {
  jars: number;
  fabricMods: number;
  minecraftVersion?: string;
  findings: Finding[];
}

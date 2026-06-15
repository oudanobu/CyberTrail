/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

export enum TrackingState {
  Idle = "Idle",
  AcquiringGPS = "AcquiringGPS",
  Active = "Active",
  Paused = "Paused",
  Saving = "Saving",
  Error = "Error"
}

export interface TraceLog {
  timestamp: string;
  level: "TRACE" | "DEBUG" | "INFO" | "WARN" | "ERROR";
  message: string;
  trace_id: string;
  module: string;
}

export interface RuleSection {
  id: string;
  title: string;
  category: "Structure" | "Dependency" | "Contract" | "State" | "Performance" | "Security";
  summary: string;
  details: string;
  forbidden: string[];
  correctExample?: string;
  incorrectExample?: string;
}

export interface CrateNode {
  id: string;
  label: string;
  description: string;
  dependencies: string[];
  forbiddenDeps: string[];
  cargoPath: string;
}

export interface CodeTemplate {
  name: string;
  description: string;
  category: string;
  isCompliant: boolean;
  code: string;
}

export interface AuditResult {
  isCompliant: boolean;
  complianceScore: number; // 0 - 100
  feedback: string;
  detectedViolations: string[];
  recommendations: string[];
  suggestedFix?: string;
}

export interface TrackPoint {
  id: string;
  lat: number;
  lng: number;
  altitude: number;
  timestamp: string;
  speed: number;
}

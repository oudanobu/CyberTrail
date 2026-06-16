/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import { useState, useMemo, useEffect, useRef } from "react";
import {
  ShieldCheck,
  Server,
  Terminal,
  Activity,
  AlertTriangle,
  Compass,
  Cpu,
  Search,
  CheckCircle,
  XCircle,
  Copy,
  ChevronRight,
  ArrowRight,
  RefreshCw,
  Clock,
  HeartPulse,
  Brain,
  CodeXml,
  HelpCircle,
  BookOpen,
  MapPin,
  MountainSnow
} from "lucide-react";
import { motion, AnimatePresence } from "motion/react";
import { TrackingState, TraceLog, RuleSection, CrateNode, CodeTemplate, TrackPoint, AuditResult } from "./types";
import { CONSTITUTION_RULES, CRATE_NODES, CODE_TEMPLATES, generateMockPoints } from "./data";

export default function App() {
  // Navigation tabs
  const [activeTab, setActiveTab] = useState<"explorer" | "graph" | "tracking" | "metrics" | "auditor" | "map">("explorer");

  // Map HUD Simulation State
  const [mapZoom, setMapZoom] = useState<number>(14);
  const [mapCenter, setMapCenter] = useState<{lat: number, lng: number}>({lat: 37.7749, lng: -122.4194});
  const [mapHistoryVisible, setMapHistoryVisible] = useState<boolean>(true);
  const [mapActiveTrack, setMapActiveTrack] = useState<{lat: number, lng: number}[]>([
    {lat: 37.7749, lng: -122.4194},
  ]);
  const [isMapRecording, setIsMapRecording] = useState<boolean>(false);
  const [mapAltitude, setMapAltitude] = useState<number>(120.0);
  const [tileServerLogs, setTileServerLogs] = useState<{time: string, type: string, message: string}[]>([
    {time: "10:52:14", type: "INFO", message: "Offline tile server daemon boot initiated"},
    {time: "10:52:15", type: "INFO", message: "Local socket binding on 127.0.0.1:8085 secured"},
    {time: "10:52:15", type: "DEBUG", message: "Registered route [/style.json] [Access-Control-Allow-Origin: *]"}
  ]);

  // Rule Explorer State
  const [selectedRule, setSelectedRule] = useState<RuleSection>(CONSTITUTION_RULES[0]);

  // Crate Link Validator State
  const [selectedCrate, setSelectedCrate] = useState<CrateNode>(CRATE_NODES[0]);
  const [targetCrate, setTargetCrate] = useState<CrateNode | null>(null);
  const [dependencyCheckResult, setDependencyCheckResult] = useState<{
    valid: boolean;
    reason: string;
  } | null>(null);

  // Tracking State Machine State
  const [currentTrackingState, setCurrentTrackingState] = useState<TrackingState>(TrackingState.Idle);
  const [syncLogs, setSyncLogs] = useState<TraceLog[]>([]);
  const [currentTraceId, setCurrentTraceId] = useState<string>("tx-unassigned");
  const logContainerRef = useRef<HTMLDivElement>(null);

  // SQLite WAL/Points Performance Metrics State
  const [trackPoints, setTrackPoints] = useState<TrackPoint[]>([]);
  const [dataPointsRendered, setDataPointsRendered] = useState<number>(0);
  const [metricsTime, setMetricsTime] = useState<number>(0);
  // Dynamic points provider
  const allPoints = useMemo(() => generateMockPoints(), []);

  // Neural Code Compliance Auditor state
  const [selectedTemplateIndex, setSelectedTemplateIndex] = useState<number>(0);
  const [codeSnippet, setCodeSnippet] = useState<string>(CODE_TEMPLATES[0].code);
  const [isAuditing, setIsAuditing] = useState<boolean>(false);
  const [auditResult, setAuditResult] = useState<AuditResult | null>(null);
  const [copiedState, setCopiedState] = useState<boolean>(false);
  const [localError, setLocalError] = useState<string | null>(null);

  // Initialize Search & Load First logs
  useEffect(() => {
    handleSimulateQuery();
    addSyncLog("INFO", "CyberTrail system daemon architecture initialized", "sys-init", "infrastructure");
  }, []);

  // Auto-scroll logging console
  useEffect(() => {
    if (logContainerRef.current) {
      logContainerRef.current.scrollTop = logContainerRef.current.scrollHeight;
    }
  }, [syncLogs]);

  // Sync Log Helper
  const addSyncLog = (
    level: TraceLog["level"],
    message: string,
    traceId: string,
    moduleName: string
  ) => {
    const newLog: TraceLog = {
      timestamp: new Date().toISOString().substring(11, 19) + "." + Math.floor(Math.random() * 1000),
      level,
      message,
      trace_id: traceId,
      module: moduleName
    };
    setSyncLogs((prev) => [...prev, newLog]);
  };

  // State transitions triggers
  const executeTrackingTransition = (target: TrackingState) => {
    const traceId = currentTraceId === "tx-unassigned" ? `trk-${Math.floor(100000 + Math.random() * 900000)}` : currentTraceId;
    if (currentTrackingState === TrackingState.Idle && target === TrackingState.AcquiringGPS) {
      setCurrentTraceId(traceId);
      setCurrentTrackingState(TrackingState.AcquiringGPS);
      addSyncLog("INFO", `Initializing GPS hardware link. trace_id = ${traceId}`, traceId, "sensors");
      addSyncLog("DEBUG", "Awaiting LocationManager cold-start satellites fix...", traceId, "sensors");
    } else if (currentTrackingState === TrackingState.AcquiringGPS && target === TrackingState.Active) {
      setCurrentTrackingState(TrackingState.Active);
      addSyncLog("INFO", "GPS Fix Acquired. 12 satellites. HDOP: 0.8. Tracking started.", traceId, "tracking");
      addSyncLog("TRACE", "Sampling point 47.6062, -122.3321. Altitude fused: 1560m.", traceId, "altitude");
    } else if (currentTrackingState === TrackingState.Active && target === TrackingState.Paused) {
      setCurrentTrackingState(TrackingState.Paused);
      addSyncLog("INFO", "Tracking Paused by User.", traceId, "application");
    } else if (target === TrackingState.Saving) {
      setCurrentTrackingState(TrackingState.Saving);
      addSyncLog("INFO", "Compressing Track log... Serializing to SQLite WAL database.", traceId, "database");
      addSyncLog("DEBUG", "Invoked StartTrackingUseCase::stop_tracking.", traceId, "application");
    } else if (target === TrackingState.Error) {
      setCurrentTrackingState(TrackingState.Error);
      addSyncLog("ERROR", "Sensor hardware disconnected unexpectedly. Location services killed.", traceId, "sensors");
    } else if (target === TrackingState.Idle) {
      setCurrentTrackingState(TrackingState.Idle);
      addSyncLog("INFO", `Track persistence complete. Transitioning back to TrackingState::Idle.`, traceId, "tracking");
      setCurrentTraceId("tx-unassigned");
    } else {
      // Direct update
      setCurrentTrackingState(target);
      addSyncLog("DEBUG", `Manual override. Transitioned to TrackingState::${target}`, traceId, "tracking");
    }
  };

  const validateDependencySelection = (source: CrateNode, target: CrateNode) => {
    setTargetCrate(target);

    if (source.id === target.id) {
      setDependencyCheckResult({
        valid: false,
        reason: "Self-dependency: A crate cannot depend on itself."
      });
      return;
    }

    // Direct forbidden checker
    if (source.forbiddenDeps.includes(target.id)) {
      setDependencyCheckResult({
        valid: false,
        reason: `CRITICAL VIOLATION: '${source.id}' is strictly FORBIDDEN from depending on '${target.id}' according to Dependency Law.`
      });
      return;
    }

    if (source.id === "domain") {
      if (target.id !== "common") {
        setDependencyCheckResult({
          valid: false,
          reason: "CRITICAL VIOLATION: Pure Domain crate represents high business isolation. It may only import `common`, no external systems or android references."
        });
        return;
      }
    }

    setDependencyCheckResult({
      valid: true,
      reason: `ACCORDANCE: '${source.id}' importing '${target.id}' is clean and complies perfectly with our strict architecture direction layout.`
    });
  };

  const handleSimulateQuery = () => {
    const start = performance.now();
    setTrackPoints(allPoints);
    setDataPointsRendered(allPoints.length);
    const end = performance.now();
    setMetricsTime(Math.min(end - start + 0.12, 12.50));
  };

  // Code Template selection helper
  const handleSelectTemplate = (index: number) => {
    setSelectedTemplateIndex(index);
    setCodeSnippet(CODE_TEMPLATES[index].code);
    setAuditResult(null);
    setLocalError(null);
  };

  // Safe neural code auditor implementation using Node proxy
  const runCodeAudit = async () => {
    setIsAuditing(true);
    setAuditResult(null);
    setLocalError(null);

    try {
      const response = await fetch("/api/audit-code", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ code: codeSnippet }),
      });

      if (!response.ok) {
        throw new Error(`Server returned error status: ${response.status}`);
      }

      const report: AuditResult = await response.json();
      setAuditResult(report);
    } catch (err: any) {
      console.error(err);
      setLocalError(err.message || "Failed to parse code snippet. Ensure server is initialized.");
    } finally {
      setIsAuditing(false);
    }
  };

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
    setCopiedState(true);
    setTimeout(() => setCopiedState(false), 2000);
  };

  return (
    <div className="min-h-screen bg-[#0d1117] text-[#c9d1d9] font-sans antialiased selection:bg-indigo-500/30 selection:text-white pb-12" id="app-root">
      
      {/* Visual Identity Header - CyberTrail Design */}
      <header className="border-b border-slate-800/80 bg-[#161b22]/90 sticky top-0 z-50 backdrop-blur-md px-6 py-4" id="app-header">
        <div className="max-w-7xl mx-auto flex flex-col md:flex-row md:items-center md:justify-between gap-4">
          
          <div className="flex items-center space-x-3">
            <div className="h-10 w-10 rounded-lg bg-emerald-600 flex items-center justify-center text-white shadow-lg shadow-emerald-600/20 glow">
              <MountainSnow className="h-6 w-6" />
            </div>
            <div>
              <div className="flex items-center space-x-2">
                <h1 className="text-xl font-semibold text-white font-mono tracking-tight">CYBERTRAIL</h1>
                <span className="bg-emerald-500/10 text-emerald-400 text-[10px] font-semibold px-2 py-0.5 rounded-full border border-emerald-500/20 font-mono">
                  CONSTITUTION v2.0
                </span>
              </div>
              <p className="text-xs text-slate-400 font-mono mt-0.5">Offline Tactical Hiking System Architecture</p>
            </div>
          </div>

          {/* Desktop Hardware Targets Info Indicators (Rule 13 Conformity) */}
          <div className="flex flex-wrap items-center gap-3">
            <div className="bg-[#0d1117] border border-slate-805 px-3 py-1.5 rounded-md flex items-center space-x-2">
              <Cpu className="h-3.5 w-3.5 text-emerald-400" />
              <div className="text-[10px] font-mono leading-tight">
                <span className="text-slate-500 block">APK GOAL</span>
                <span className="text-slate-300 font-semibold uppercase">&lt; 20MB</span>
              </div>
            </div>
            
            <div className="bg-[#0d1117] border border-slate-805 px-3 py-1.5 rounded-md flex items-center space-x-2">
              <HeartPulse className="h-3.5 w-3.5 text-indigo-400" />
              <div className="text-[10px] font-mono leading-tight">
                <span className="text-slate-500 block">SLA RAM LIMIT</span>
                <span className="text-indigo-400 font-semibold">&lt; 100MB</span>
              </div>
            </div>

            <div className="bg-[#0d1117] border border-slate-805 px-3 py-1.5 rounded-md flex items-center space-x-2">
              <Activity className="h-3.5 w-3.5 text-amber-400" />
              <div className="text-[10px] font-mono leading-tight">
                <span className="text-slate-500 block">SLA BOOT SPEED</span>
                <span className="text-amber-300 font-semibold">&lt; 1s</span>
              </div>
            </div>
          </div>

        </div>
      </header>

      {/* Hero Quick Banner */}
      <section className="bg-[#161b22]/40 border-b border-slate-800/40 px-6 py-6 font-mono text-xs text-slate-400" id="constitution-hero">
        <div className="max-w-7xl mx-auto flex flex-col md:flex-row items-center justify-between gap-4">
          <div className="flex items-center space-x-2 text-slate-300">
            <BookOpen className="h-4 w-4 text-emerald-400" />
            <span>CyberTrail mandates single responsibility principles restricting direct UI access to Core Data.</span>
          </div>
          <div className="flex items-center space-x-4">
            <span className="text-emerald-400 flex items-center space-x-1">
              <span className="h-2 w-2 rounded-full bg-emerald-500 animate-pulse mr-1" />
              Active System Guard
            </span>
            <span className="text-slate-500">|</span>
            <span className="text-[10px] bg-slate-800 px-2 py-1 rounded text-slate-300">Target Platform: Android & NDK</span>
          </div>
        </div>
      </section>

      {/* Main Container Layout */}
      <main className="max-w-7xl mx-auto px-6 mt-8" id="dashboard-layout">
        
        {/* Navigation Tabs bar */}
        <div className="flex flex-wrap border-b border-slate-810 mb-8 gap-1">
          <button
            id="tab-btn-explorer"
            onClick={() => setActiveTab("explorer")}
            className={`flex items-center space-x-2 px-5 py-3 text-sm font-medium border-b-2 transition-all font-mono ${
              activeTab === "explorer"
                ? "border-emerald-500 text-white bg-emerald-500/10"
                : "border-transparent text-slate-400 hover:text-slate-200 hover:bg-slate-800/30"
            }`}
          >
            <Compass className="h-4 w-4" />
            <span>1. Rules Explorer</span>
          </button>
          
          <button
            id="tab-btn-graph"
            onClick={() => setActiveTab("graph")}
            className={`flex items-center space-x-2 px-5 py-3 text-sm font-medium border-b-2 transition-all font-mono ${
              activeTab === "graph"
                ? "border-emerald-500 text-white bg-emerald-500/10"
                : "border-transparent text-slate-400 hover:text-slate-200 hover:bg-slate-800/30"
            }`}
          >
            <Activity className="h-4 w-4" />
            <span>2. Crate Law Mapper</span>
          </button>

          <button
            id="tab-btn-tracking"
            onClick={() => setActiveTab("tracking")}
            className={`flex items-center space-x-2 px-5 py-3 text-sm font-medium border-b-2 transition-all font-mono ${
              activeTab === "tracking"
                ? "border-emerald-500 text-white bg-emerald-500/10"
                : "border-transparent text-slate-400 hover:text-slate-200 hover:bg-slate-800/30"
            }`}
          >
            <MapPin className="h-4 w-4" />
            <span>3. Core Tracking State</span>
          </button>

          <button
            id="tab-btn-metrics"
            onClick={() => setActiveTab("metrics")}
            className={`flex items-center space-x-2 px-5 py-3 text-sm font-medium border-b-2 transition-all font-mono ${
              activeTab === "metrics"
                ? "border-emerald-500 text-white bg-emerald-500/10"
                : "border-transparent text-slate-400 hover:text-slate-200 hover:bg-slate-800/30"
            }`}
          >
            <Search className="h-4 w-4" />
            <span>4. MVP SQLite Points Validation</span>
          </button>

          <button
            id="tab-btn-auditor"
            onClick={() => setActiveTab("auditor")}
            className={`flex items-center space-x-2 px-5 py-3 text-sm font-medium border-b-2 transition-all font-mono relative ${
              activeTab === "auditor"
                ? "border-emerald-500 text-white bg-emerald-500/10"
                : "border-transparent text-slate-400 hover:text-slate-200 hover:bg-slate-800/30"
            }`}
          >
            <Brain className="h-4 w-4 text-emerald-400" />
            <span>5. Neural Compiler Auditor</span>
            <span className="absolute -top-1 -right-1 bg-emerald-500 text-[8px] font-bold text-white px-1 py-0.5 rounded blink">
              GEMINI 3.5
            </span>
          </button>

          <button
            id="tab-btn-map"
            onClick={() => setActiveTab("map")}
            className={`flex items-center space-x-2 px-5 py-3 text-sm font-medium border-b-2 transition-all font-mono relative ${
              activeTab === "map"
                ? "border-emerald-500 text-white bg-emerald-500/10"
                : "border-transparent text-slate-400 hover:text-slate-200 hover:bg-slate-800/30"
            }`}
          >
            <Compass className="h-4 w-4 text-emerald-400" />
            <span>6. Off-Grid Map HUD</span>
            <span className="absolute -top-1 -right-1 bg-indigo-500 text-[8px] font-bold text-white px-1 py-0.5 rounded blink">
              PHASE 2
            </span>
          </button>
        </div>

        {/* Action Content Area */}
        <div className="min-h-[500px]" id="dashboard-content">
          
          {/* TAB 1: CONSTITUTION RULES EXPLORER */}
          {activeTab === "explorer" && (
            <motion.div
              initial={{ opacity: 0, y: 15 }}
              animate={{ opacity: 1, y: 0 }}
              className="grid grid-cols-1 lg:grid-cols-12 gap-8"
              id="rules-explorer-view"
            >
              {/* Left rules index list side */}
              <div className="lg:col-span-5 space-y-3">
                <h3 className="text-sm font-bold text-slate-400 font-mono tracking-wider uppercase mb-3">Constitution Rules</h3>
                <div className="space-y-2">
                  {CONSTITUTION_RULES.map((rule) => (
                      <button
                      key={rule.id}
                      onClick={() => setSelectedRule(rule)}
                      className={`w-full text-left p-4 rounded-lg flex items-center justify-between transition-all border ${
                        selectedRule.id === rule.id
                          ? "bg-emerald-600/10 border-emerald-500 text-white shadow-md shadow-emerald-600/5"
                          : "bg-[#161b22] border-slate-800 hover:bg-[#1f242c] hover:border-slate-700 text-slate-300"
                      }`}
                    >
                      <div className="flex items-center space-x-3">
                        <div className={`h-8 w-8 rounded-md flex items-center justify-center text-xs font-mono font-bold ${
                          selectedRule.id === rule.id ? "bg-emerald-600 text-white" : "bg-slate-850 text-slate-400 border border-slate-700"
                        }`}>
                          {rule.id.split("-")[1]}
                        </div>
                        <div>
                          <span className="font-medium font-mono text-sm block">{rule.title.replace(/^\d+\.\s*/, "")}</span>
                          <span className="text-[10px] text-slate-500 font-sans tracking-wide">{rule.category} Layer</span>
                        </div>
                      </div>
                      <ChevronRight className="h-4 w-4 text-slate-500" />
                    </button>
                  ))}
                </div>
              </div>

              {/* Right rules detailed spec board */}
              <div className="lg:col-span-7">
                <div className="bg-[#161b22] border border-slate-800 rounded-xl p-6 shadow-xl relative overflow-hidden">
                  
                  {/* Decorative background watermark */}
                  <div className="absolute top-0 right-0 p-8 text-8xl font-black font-mono text-slate-800/10 select-none pointer-events-none">
                    RULE
                  </div>

                  <div className="flex items-center justify-between border-b border-slate-800 pb-4 mb-5">
                    <div>
                      <span className="text-[10px] uppercase font-mono tracking-widest text-emerald-400 bg-emerald-500/10 px-2 py-1 rounded">
                        Constitution Rule Code: {selectedRule.id.toUpperCase()}
                      </span>
                      <h2 className="text-xl font-semibold text-white font-mono mt-1.5">{selectedRule.title}</h2>
                    </div>
                    <div className="text-xs font-mono bg-slate-800 text-slate-300 px-3 py-1.5 rounded-md border border-slate-700">
                      SLA Priority: {selectedRule.category}
                    </div>
                  </div>

                  <div className="space-y-6">
                    <div>
                      <h4 className="text-xs font-bold font-mono tracking-wider text-slate-400 uppercase mb-2">Architectural Summary</h4>
                      <p className="text-sm text-slate-300 font-sans leading-relaxed">{selectedRule.summary}</p>
                    </div>

                    <div>
                      <h4 className="text-xs font-bold font-mono tracking-wider text-slate-400 uppercase mb-2">Deep Implementation Matrix</h4>
                      <p className="text-sm text-slate-400 font-sans leading-relaxed whitespace-pre-line">{selectedRule.details}</p>
                    </div>

                    <div className="border-t border-slate-800 pb-4" />

                    {/* Strict Negative Assertions */}
                    <div>
                      <h4 className="text-xs font-bold font-mono tracking-wider text-rose-400 uppercase flex items-center space-x-1.5 mb-3">
                        <AlertTriangle className="h-3.5 w-3.5" />
                        <span>Strictly FORBIDDEN Activities ({selectedRule.category})</span>
                      </h4>
                      <ul className="grid grid-cols-1 md:grid-cols-2 gap-2 text-xs font-sans">
                        {selectedRule.forbidden.map((f, idx) => (
                          <li key={idx} className="bg-rose-500/5 text-rose-300 border border-rose-500/10 px-3 py-2 rounded-md flex items-start space-x-2">
                            <span className="h-1.5 w-1.5 rounded-full bg-rose-500/80 mt-1.5 flex-shrink-0" />
                            <span>{f}</span>
                          </li>
                        ))}
                      </ul>
                    </div>

                    {/* Standard Examples side-by-side spec */}
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mt-4 font-mono text-[11px]">
                      <div>
                        <div className="text-rose-400 bg-rose-500/5 border-l-2 border-rose-500 px-3 py-1.5 mb-2 font-semibold">
                          ❌ VIOLATING CODE PATTERN
                        </div>
                        <pre className="bg-[#0d1117] border border-slate-800 rounded-md p-3 overflow-x-auto text-rose-300 max-h-48 scrolling">
                          <code>{selectedRule.incorrectExample}</code>
                        </pre>
                      </div>

                      <div>
                        <div className="text-emerald-400 bg-emerald-500/5 border-l-2 border-emerald-500 px-3 py-1.5 mb-2 font-semibold">
                          ✅ COMPLIANT ARCHITECTURE
                        </div>
                        <pre className="bg-[#0d1117] border border-slate-800 rounded-md p-3 overflow-x-auto text-emerald-300 max-h-48 scrolling">
                          <code>{selectedRule.correctExample}</code>
                        </pre>
                      </div>
                    </div>

                  </div>
                </div>
              </div>
            </motion.div>
          )}

          {/* TAB 2: CRATE LAW MAPPER */}
          {activeTab === "graph" && (
            <motion.div
              initial={{ opacity: 0, y: 15 }}
              animate={{ opacity: 1, y: 0 }}
              className="space-y-8"
              id="dependency-graph-view"
            >
              <div className="bg-[#161b22] border border-slate-800 rounded-xl p-6">
                <div className="flex items-center space-x-2 text-emerald-400 mb-2">
                  <Activity className="h-5 w-5" />
                  <span className="text-sm font-mono font-bold uppercase tracking-wider">Dependency Direction Layer Audits</span>
                </div>
                <h3 className="text-lg font-semibold text-white font-mono">Workspace Boundary Crate Validator</h3>
                <p className="text-xs text-slate-400 font-sans mt-1">
                  Select a source crate and a target crate from the layout grid to calculate architectural dependencies, validation trails and direct compilation block compliance under Rule 2 (Dependency Law).
                </p>

                {/* Source selection grid slider */}
                <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mt-8">
                  
                  {/* Step A: Select Source (Importing Crate) */}
                  <div className="bg-[#0d1117] border border-slate-800 rounded-lg p-4">
                    <span className="text-[10px] font-mono font-bold text-emerald-400 uppercase block mb-3 bg-emerald-500/5 px-2 py-1 rounded w-fit">
                      Step 1: Context Importing Crate
                    </span>
                    <label className="text-xs text-slate-400 block mb-2 font-mono">Source Crate Folder:</label>
                    <div className="space-y-1.5 max-h-72 overflow-y-auto pr-1">
                      {CRATE_NODES.map((node) => (
                        <button
                          key={node.id}
                          onClick={() => {
                            setSelectedCrate(node);
                            setDependencyCheckResult(null);
                            setTargetCrate(null);
                          }}
                          className={`w-full text-left p-2 rounded text-xs transition-all font-mono border flex items-center justify-between ${
                            selectedCrate.id === node.id
                              ? "bg-emerald-600/20 border-emerald-500 text-white font-semibold"
                              : "bg-[#161b22] border-slate-800 hover:bg-[#1f242c] text-slate-400"
                          }`}
                        >
                          <span>{node.cargoPath}</span>
                          <span className="text-[9px] bg-slate-800 px-1 py-0.5 rounded text-slate-500">
                            {node.id}
                          </span>
                        </button>
                      ))}
                    </div>
                  </div>

                  {/* Step B: Select Target (Imported dependency) */}
                  <div className="bg-[#0d1117] border border-slate-800 rounded-lg p-4">
                    <span className="text-[10px] font-mono font-bold text-amber-400 uppercase block mb-3 bg-amber-500/5 px-2 py-1 rounded w-fit">
                      Step 2: Dependency Library
                    </span>
                    <label className="text-xs text-slate-400 block mb-2 font-mono">Target Dependency Library:</label>
                    <div className="space-y-1.5 max-h-72 overflow-y-auto pr-1">
                      {CRATE_NODES.map((node) => (
                        <button
                          key={node.id}
                          onClick={() => validateDependencySelection(selectedCrate, node)}
                          className={`w-full text-left p-2 rounded text-xs transition-all font-mono border flex items-center justify-between ${
                            targetCrate?.id === node.id
                              ? "bg-amber-500/20 border-amber-500 text-white font-semibold"
                              : "bg-[#161b22] border-slate-800 hover:bg-[#1f242c] text-slate-400"
                          }`}
                        >
                          <span>{node.cargoPath}</span>
                          <span className="text-[9px] bg-slate-805 px-1.5 py-0.5 rounded text-slate-500">
                            {node.id}
                          </span>
                        </button>
                      ))}
                    </div>
                  </div>

                  {/* Step C: Validation Result Audit Check */}
                  <div className="bg-[#0d1117] border border-slate-800 rounded-lg p-5 flex flex-col justify-between">
                    <div>
                      <span className="text-[10px] font-mono font-bold text-slate-400 uppercase block mb-3">
                        Rule 2 Compliance Report
                      </span>
                      
                      {!targetCrate ? (
                        <div className="text-center py-10 text-slate-500">
                          <HelpCircle className="h-10 w-10 mx-auto text-slate-600 mb-2" />
                          <p className="text-xs font-mono">Please click on a Target Crate in Step 2 to execute link auditing checks.</p>
                        </div>
                      ) : (
                        <div className="space-y-4">
                          <div>
                            <span className="text-[10px] block text-slate-500 uppercase font-mono">Formula Attempted</span>
                            <div className="text-sm font-mono text-slate-200 font-bold flex items-center space-x-2 mt-1">
                              <span>crates/{selectedCrate.id}</span>
                              <ArrowRight className="h-3.5 w-3.5 text-indigo-400" />
                              <span className="bg-slate-800 px-1 rounded text-amber-300">crates/{targetCrate.id}</span>
                            </div>
                          </div>

                          <div className="border-t border-slate-800 my-2" />

                          {dependencyCheckResult?.valid ? (
                            <div className="bg-emerald-500/5 border border-emerald-500/20 p-4 rounded-md">
                              <div className="flex items-center space-x-2 text-emerald-400 text-sm font-semibold mb-1">
                                <CheckCircle className="h-4 w-4" />
                                <span>COMPLIANT ACCORDANCE</span>
                              </div>
                              <p className="text-xs text-emerald-300 font-sans leading-relaxed">
                                {dependencyCheckResult.reason}
                              </p>
                            </div>
                          ) : (
                            <div className="bg-rose-500/5 border border-rose-500/20 p-4 rounded-md">
                              <div className="flex items-center space-x-2 text-rose-400 text-sm font-semibold mb-1">
                                <XCircle className="h-4 w-4" />
                                <span>CONSTITUTION VIOLATION</span>
                              </div>
                              <p className="text-xs text-rose-300 font-sans leading-relaxed">
                                {dependencyCheckResult?.reason}
                              </p>
                            </div>
                          )}
                        </div>
                      )}
                    </div>

                    <div className="border-t border-slate-800/80 pt-4 mt-4">
                      <div className="text-[10px] uppercase font-mono text-slate-500 tracking-wider">Dependency direction guideline:</div>
                      <div className="text-[10px] text-slate-400 font-sans mt-1">
                        UI layer accesses Application. Application coordinates Domain via interfaces. Infrastructure implements interfaces downwards. No sideways cycles.
                      </div>
                    </div>

                  </div>

                </div>

                {/* Flow Visualizer Block */}
                <div className="mt-8 border-t border-slate-800/80 pt-6">
                  <h4 className="text-xs font-bold text-slate-400 font-mono uppercase mb-4 tracking-wider">Architecture Hierarchy Stack (Visual representation)</h4>
                  
                  <div className="grid grid-cols-1 md:grid-cols-4 gap-4 text-center font-mono text-xs">
                    
                    <div className="border border-slate-800 rounded bg-[#0d1117]/60 p-4">
                      <div className="text-[10px] text-slate-500 tracking-widest block uppercase mb-1">Android App (UI)</div>
                      <div className="bg-emerald-600/10 text-emerald-400 px-3 py-2 rounded-md font-semibold border border-emerald-500/20">
                        android/ UI Layer
                      </div>
                      <div className="text-[10px] text-slate-500 mt-2">Cannot directly access database or sqlx drivers! Extracted via FFI.</div>
                    </div>

                    <div className="border border-emerald-500/20 rounded bg-[#0d1117] p-4 relative flex flex-col justify-between">
                      <div className="absolute top-1/2 -left-3 transform -translate-y-1/2 hidden md:block">
                        <ArrowRight className="h-5 w-5 text-emerald-500" />
                      </div>
                      <div>
                        <div className="text-[10px] text-slate-500 tracking-widest block uppercase mb-1">Application UseCases</div>
                        <div className="bg-emerald-600/20 text-white px-3 py-2 rounded-md font-semibold border border-emerald-500/30">
                          crates/application
                        </div>
                      </div>
                      <div className="text-[10px] text-slate-500 mt-2">Binds workflows (StartTracking) and interacts via Polymorphic Repositories.</div>
                    </div>

                    <div className="border border-slate-800 rounded bg-[#0d1117]/60 p-4 relative flex flex-col justify-between">
                      <div className="absolute top-1/2 -left-3 transform -translate-y-1/2 hidden md:block">
                        <ArrowRight className="h-5 w-5 text-emerald-500" />
                      </div>
                      <div>
                        <div className="text-[10px] text-slate-500 tracking-widest block uppercase mb-1">Pure Domain Logic</div>
                        <div className="bg-[#1c2128] text-emerald-300 px-3 py-2 rounded-md font-semibold border border-slate-700">
                          crates/domain
                        </div>
                      </div>
                      <div className="text-[10px] text-slate-400 mt-2">Entities like Track & Anchor. Zero infrastructure capabilities.</div>
                    </div>

                    <div className="border border-slate-800 rounded bg-[#0d1117]/60 p-4 relative flex flex-col justify-between">
                      <div className="absolute top-1/2 -left-3 transform -translate-y-1/2 hidden md:block border-slate-800">
                        {/* bidirectional path indicators */}
                        <div className="text-emerald-400 text-xs font-bold font-mono">← implements</div>
                      </div>
                      <div>
                        <div className="text-[10px] text-slate-500 tracking-widest block uppercase mb-1">Infrastructure Interfaces</div>
                        <div className="bg-[#1c2128] text-slate-300 px-3 py-2 rounded-md font-semibold border border-slate-700">
                          crates/database, crates/sensors
                        </div>
                      </div>
                      <div className="text-[10px] text-slate-500 mt-2">Provides concrete impls for SQLite & Hardware Sensors.</div>
                    </div>

                  </div>
                </div>

              </div>
            </motion.div>
          )}

          {/* TAB 3: TRACKING ENGINE STATE SIMULATOR */}
          {activeTab === "tracking" && (
            <motion.div
              initial={{ opacity: 0, y: 15 }}
              animate={{ opacity: 1, y: 0 }}
              className="grid grid-cols-1 lg:grid-cols-12 gap-8"
              id="tracking-state-view"
            >
              {/* Left Column State machine display map */}
              <div className="lg:col-span-4 bg-[#161b22] border border-slate-800 rounded-xl p-6 relative">
                <span className="text-[10px] text-emerald-400 font-mono tracking-wider font-bold mb-1 uppercase block bg-emerald-500/10 w-fit px-2 py-0.5 rounded">
                  Core State Loop
                </span>
                <h3 className="text-lg font-semibold text-white font-mono">Tracking Engine States</h3>
                <p className="text-xs text-slate-400 mt-1 mb-6 font-sans">
                  The Tracking Engine operates via isolated state transitions preventing rogue GPS calls or uncoordinated tracking pauses.
                </p>

                {/* Array of States representing atomic states */}
                <div className="space-y-2">
                  {Object.values(TrackingState).map((st) => {
                    const isActive = currentTrackingState === st;
                    return (
                      <div
                        key={st}
                        className={`p-3 rounded-lg border transition-all text-xs font-mono flex items-center justify-between ${
                          isActive
                            ? "bg-emerald-600/20 border-emerald-500 text-white font-bold"
                            : "bg-[#0d1117] border-slate-810 text-slate-500 opacity-60"
                        }`}
                      >
                        <div className="flex items-center space-x-2">
                          <span className={`h-2.5 w-2.5 rounded-full ${
                            isActive ? "bg-emerald-500 animate-ping" : "bg-slate-700"
                          }`} />
                          <span>TrackingState::{st}</span>
                        </div>
                        {isActive && (
                          <span className="bg-emerald-500/10 text-emerald-400 px-1.5 py-0.5 text-[9px] font-semibold border border-emerald-500/20 rounded font-mono">
                            ACTIVE
                          </span>
                        )}
                      </div>
                    );
                  })}
                </div>
              </div>

              {/* Right Column: Interaction console and tracing simulator logs */}
              <div className="lg:col-span-8 flex flex-col space-y-6">
                
                {/* Simulated Triggers Dashboard Panel */}
                <div className="bg-[#161b22] border border-slate-805 rounded-xl p-6">
                  <h4 className="text-xs font-bold text-slate-400 font-mono uppercase mb-4 tracking-wider">Tracking State Control Unit</h4>
                  
                  <div className="flex flex-wrap gap-2">
                    {/* Transitions button blocks */}
                    <button
                      id="btn-trigger-gps"
                      disabled={currentTrackingState !== TrackingState.Idle}
                      onClick={() => executeTrackingTransition(TrackingState.AcquiringGPS)}
                      className="px-4 py-2 rounded-md bg-emerald-600 hover:bg-emerald-500 text-white font-mono text-xs font-semibold disabled:bg-slate-800 disabled:text-slate-500 disabled:cursor-not-allowed transition-all"
                    >
                      Start GPS Hook
                    </button>

                    <button
                      id="btn-trigger-active"
                      disabled={currentTrackingState !== TrackingState.AcquiringGPS && currentTrackingState !== TrackingState.Paused}
                      onClick={() => executeTrackingTransition(TrackingState.Active)}
                      className="px-4 py-2 rounded-md bg-indigo-600 hover:bg-indigo-500 text-white font-mono text-xs font-semibold disabled:opacity-50 disabled:cursor-not-allowed transition-all"
                    >
                      GPS Acquired (Track)
                    </button>

                    <button
                      id="btn-trigger-pause"
                      disabled={currentTrackingState !== TrackingState.Active}
                      onClick={() => executeTrackingTransition(TrackingState.Paused)}
                      className="px-4 py-2 rounded-md bg-amber-600 hover:bg-amber-500 text-white font-mono text-xs font-semibold disabled:opacity-50 disabled:cursor-not-allowed transition-all"
                    >
                      Pause Collection
                    </button>

                    <button
                      id="btn-trigger-save"
                      disabled={currentTrackingState === TrackingState.Idle || currentTrackingState === TrackingState.Error || currentTrackingState === TrackingState.Saving}
                      onClick={() => executeTrackingTransition(TrackingState.Saving)}
                      className="px-4 py-2 rounded-md bg-slate-700 hover:bg-slate-600 text-white font-mono text-xs font-semibold disabled:opacity-50 disabled:cursor-not-allowed transition-all"
                    >
                      Stop & Save Route
                    </button>

                    <button
                      id="btn-trigger-fail"
                      disabled={currentTrackingState === TrackingState.Idle || currentTrackingState === TrackingState.Error}
                      onClick={() => executeTrackingTransition(TrackingState.Error)}
                      className="px-4 py-2 rounded-md bg-rose-600 hover:bg-rose-500 text-white font-mono text-xs font-semibold disabled:opacity-50 disabled:cursor-not-allowed transition-all"
                    >
                      Simulate Error
                    </button>

                    <button
                      id="btn-trigger-idle"
                      disabled={currentTrackingState === TrackingState.Idle}
                      onClick={() => executeTrackingTransition(TrackingState.Idle)}
                      className="px-4 py-2 rounded-md bg-slate-800 hover:bg-slate-700 text-slate-300 font-mono text-xs font-semibold disabled:opacity-50 disabled:cursor-not-allowed transition-all"
                    >
                      Clear to Idle
                    </button>
                  </div>
                </div>

                {/* Observability Console Pane (Rule 8 conformance) */}
                <div className="bg-[#0d1117] border border-slate-800 rounded-xl p-5 flex flex-col h-[340px] shadow-2xl relative">
                  
                  {/* Console Header bar */}
                  <div className="flex items-center justify-between border-b border-slate-800 pb-3 mb-3">
                    <div className="flex items-center space-x-2">
                      <Terminal className="h-4 w-4 text-emerald-400" />
                      <span className="text-xs text-emerald-400 font-mono uppercase font-bold">Standard Logging & Observability Console (Rule 8)</span>
                    </div>
                    <div className="flex items-center space-x-3 text-[10px] font-mono">
                      <span className="text-slate-500">Trace ID: <span className="text-emerald-400">{currentTraceId}</span></span>
                      <button
                        onClick={() => {
                          setSyncLogs([]);
                          addSyncLog("INFO", "Log streams flushed.", "tx-system", "sync");
                        }}
                        className="text-slate-400 hover:text-white underline"
                      >
                        Clear console
                      </button>
                    </div>
                  </div>

                  {/* Terminal stdout view */}
                  <div
                    ref={logContainerRef}
                    className="flex-1 overflow-y-auto font-mono text-[11px] leading-relaxed space-y-1 bg-[#090d12]/80 p-3 rounded border border-slate-810/50 scrolling"
                  >
                    {syncLogs.length === 0 ? (
                      <div className="text-slate-600 text-center py-20">Console idle. Awaiting compilation or state machine interaction.</div>
                    ) : (
                      syncLogs.map((log, idx) => {
                        const levelColors = {
                          TRACE: "text-slate-500",
                          DEBUG: "text-emerald-400",
                          INFO: "text-emerald-400",
                          WARN: "text-amber-400",
                          ERROR: "text-rose-500 font-semibold"
                        };

                        return (
                          <div key={idx} className="hover:bg-slate-800/20 px-1 rounded flex items-start space-x-2 border-b border-slate-900/50">
                            <span className="text-slate-500 flex-shrink-0">[{log.timestamp}]</span>
                            <span className={`${levelColors[log.level]} font-semibold flex-shrink-0 w-12 text-center`}>
                              {log.level}
                            </span>
                            <span className="text-emerald-300/85 flex-shrink-0">[{log.module}]</span>
                            <span className="text-slate-300 flex-1">{log.message}</span>
                            <span className="text-slate-500 text-[10px] flex-shrink-0 font-light">({log.trace_id})</span>
                          </div>
                        );
                      })
                    )}
                  </div>

                  <div className="text-[10px] text-slate-500 font-mono mt-2 text-right">
                    Conforms strictly with Trace Levels: TRACE, DEBUG, INFO, WARN, ERROR. Direct naked println! calls blocked.
                  </div>

                </div>

              </div>
            </motion.div>
          )}

          {/* TAB 4: SQLITE PERFORMANCE METRICS DEMO */}
          {activeTab === "metrics" && (
            <motion.div
              initial={{ opacity: 0, y: 15 }}
              animate={{ opacity: 1, y: 0 }}
              className="space-y-6"
              id="sqlite-metrics-view"
            >
              <div className="bg-[#161b22] border border-slate-800 rounded-xl p-6">
                
                <div className="flex flex-col md:flex-row md:items-center md:justify-between border-b border-slate-800 pb-4 mb-6 gap-4">
                  <div>
                    <span className="text-[10px] text-emerald-400 font-mono tracking-wider font-bold uppercase block bg-emerald-500/10 w-fit px-2 py-0.5 rounded mb-1">
                      Rule 7 WAL Mode Validator
                    </span>
                    <h3 className="text-lg font-semibold text-white font-mono">SQLite Bulk Writes Validation Metrics</h3>
                    <p className="text-xs text-slate-400 font-sans mt-0.5">
                      Ensures point insertions operate seamlessly alongside Canvas read calls efficiently.
                    </p>
                  </div>
                  
                  {/* Points size context */}
                  <div className="flex space-x-6 text-xs font-mono bg-[#0d1117] border border-slate-800 px-4 py-2 rounded-md">
                    <div>
                      <span className="text-slate-500 block">RENDERED POINTS</span>
                      <span className="text-emerald-400 font-bold">{dataPointsRendered}</span>
                    </div>
                    <div>
                      <span className="text-slate-500 block">SIM TIME</span>
                      <span className="text-amber-400 font-bold">{metricsTime.toFixed(2)} ms</span>
                    </div>
                  </div>
                </div>

                {/* Input Search Vector Bar */}
                <div className="flex items-center space-x-3 mb-6">
                  <div className="relative flex-1">
                    <button onClick={handleSimulateQuery} className="bg-emerald-600 hover:bg-emerald-500 text-white font-semibold font-mono text-xs px-4 py-2 mt-2 rounded transition-all">Reload Simulation</button>
                  </div>
                </div>

                {/* Table representation */}
                <div className="border border-slate-800 rounded-lg overflow-hidden bg-[#0d1117]">
                  <table className="w-full text-left font-mono">
                    <thead className="bg-[#161b22] border-b border-slate-800 text-[10px] text-slate-400 tracking-wider">
                      <tr>
                        <th className="px-4 py-3 font-semibold w-24">POINT ID</th>
                        <th className="px-4 py-3 font-semibold">LATITUDE</th>
                        <th className="px-4 py-3 font-semibold">LONGITUDE</th>
                        <th className="px-4 py-3 font-semibold w-24">ALTITUDE</th>
                        <th className="px-4 py-3 font-semibold w-24">SPEED</th>
                        <th className="px-4 py-3 font-semibold text-right">TIMESTAMP</th>
                      </tr>
                    </thead>
                    <tbody className="text-xs text-slate-300 divide-y divide-slate-800/60">
                      {trackPoints.slice(0, 10).map((point) => (
                        <tr key={point.id} className="hover:bg-slate-800/30 transition-colors">
                          <td className="px-4 py-3 text-emerald-400">{point.id}</td>
                          <td className="px-4 py-3">{point.lat.toFixed(5)}</td>
                          <td className="px-4 py-3">{point.lng.toFixed(5)}</td>
                          <td className="px-4 py-3">{point.altitude.toFixed(1)}m</td>
                          <td className="px-4 py-3">{point.speed.toFixed(1)} m/s</td>
                          <td className="px-4 py-3 text-right text-slate-500 text-[10px]">
                            {new Date(point.timestamp).toLocaleTimeString()}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                  {trackPoints.length > 10 && (
                     <div className="px-4 py-2 text-center text-slate-500 font-mono text-[10px] bg-[#161b22]">
                        Showing 10 latest active points out of {trackPoints.length} total history traces loaded.
                     </div>
                  )}
                </div>

              </div>
            </motion.div>
          )}

          {/* TAB 5: NEURAL COMPILER AUDITOR */}
          {activeTab === "auditor" && (
            <motion.div
              initial={{ opacity: 0, y: 15 }}
              animate={{ opacity: 1, y: 0 }}
              className="grid grid-cols-1 lg:grid-cols-12 gap-8"
              id="neural-auditor-view"
            >
              {/* Left Column: Editor controls and standard selection */}
              <div className="lg:col-span-6 space-y-4">
                <div className="bg-[#161b22] border border-slate-800 rounded-xl p-6">
                  
                  <div className="flex items-center justify-between mb-4">
                    <div className="flex items-center space-x-2">
                      <CodeXml className="h-5 w-5 text-emerald-400" />
                      <h3 className="text-sm font-bold text-white font-mono tracking-wider uppercase">Rust Source Core Unit</h3>
                    </div>
                    <span className="text-[10px] text-slate-500 font-mono">Standard target: Rust stable</span>
                  </div>

                  <p className="text-xs text-slate-405 mb-4 font-sans leading-relaxed">
                    Select a pre-configured template snippet or input custom Rust code blocks directly. The neural processor integrates the CyberTrail Offline Tactical Hiking System rules to output compile-audits.
                  </p>

                  {/* Templates dropdown selector */}
                  <div className="mb-4">
                    <label className="text-[10px] font-mono tracking-wider uppercase text-slate-500 block mb-2">Select Template:</label>
                    <select
                      id="template-selector"
                      value={selectedTemplateIndex}
                      onChange={(e) => handleSelectTemplate(Number(e.target.value))}
                      className="w-full bg-[#0d1117] border border-slate-800 rounded-lg p-2.5 text-xs text-slate-300 font-mono focus:border-emerald-500 focus:outline-none"
                    >
                      {CODE_TEMPLATES.map((tmpl, idx) => (
                        <option key={idx} value={idx}>
                          {tmpl.isCompliant ? "[COMPLIANT] " : "[VIOLATION] "} {tmpl.name}
                        </option>
                      ))}
                    </select>
                  </div>

                  {/* Sandbox code container field */}
                  <div className="relative">
                    <textarea
                      id="rust-code-editor"
                      rows={14}
                      value={codeSnippet}
                      onChange={(e) => {
                        setCodeSnippet(e.target.value);
                        setAuditResult(null);
                        setLocalError(null);
                      }}
                      className="w-full bg-[#0d1117] border border-slate-800 text-xs text-slate-300 font-mono p-4 rounded-lg focus:border-emerald-500 focus:outline-none leading-relaxed placeholder-slate-650"
                    />
                    
                    {/* Floating template status indicator */}
                    <div className="absolute top-2.5 right-2.5 flex items-center space-x-2">
                      <button
                        onClick={() => copyToClipboard(codeSnippet)}
                        className="p-1 px-2 text-[9px] font-mono bg-slate-800 text-slate-300 border border-slate-700 hover:text-white rounded transition"
                        title="Copy code snippet"
                      >
                        {copiedState ? "Copied" : "Copy Source"}
                      </button>
                    </div>
                  </div>

                  {/* Run neural compiler button */}
                  <div className="flex items-center justify-between mt-4">
                    <div className="flex items-center space-x-2 text-xs font-mono text-slate-500">
                      <Brain className="h-3.5 w-3.5 text-emerald-400" />
                      <span>Model pipeline: gemini-3.5-flash</span>
                    </div>
                    
                    <button
                      id="btn-trigger-ai-audit"
                      disabled={isAuditing}
                      onClick={runCodeAudit}
                      className="bg-emerald-600 hover:bg-emerald-500 disabled:bg-slate-800 disabled:text-slate-500 text-white px-5 py-2.5 rounded-lg text-xs font-mono font-bold tracking-wider flex items-center space-x-2 transition shadow-lg shadow-emerald-600/10"
                    >
                      {isAuditing ? (
                        <>
                          <RefreshCw className="h-3.5 w-3.5 animate-spin mr-1" />
                          <span>Compiling Audits...</span>
                        </>
                      ) : (
                        <>
                          <Brain className="h-3.5 w-3.5 mr-1" />
                          <span>Neural Compliance Check</span>
                        </>
                      )}
                    </button>
                  </div>

                </div>
              </div>

              {/* Right Column: Dynamic Gemini response reports */}
              <div className="lg:col-span-6">
                
                {localError && (
                  <div className="bg-rose-500/5 border border-rose-500/20 p-5 rounded-xl text-rose-300 text-xs font-mono mb-4">
                    <h4 className="text-sm font-bold text-rose-400 uppercase flex items-center space-x-1.5 mb-2">
                      <XCircle className="h-4 w-4" />
                      <span>Audit Execution Interrupted</span>
                    </h4>
                    <p className="leading-relaxed mb-3">{localError}</p>
                    <div className="bg-[#0d1117] p-3 rounded text-[10px] text-slate-400">
                      Make sure your development server is active, and ensure that your <code>GEMINI_API_KEY</code> has been configured securely inside your settings.
                    </div>
                  </div>
                )}

                {/* Display Placeholder before audits */}
                {!auditResult && !isAuditing && (
                  <div className="bg-[#161b22] border border-slate-800 border-dashed rounded-xl p-8 flex flex-col items-center justify-center h-full min-h-[400px] text-center text-slate-500">
                    <Brain className="h-12 w-12 text-slate-600 mb-3 animate-pulse" />
                    <h4 className="text-sm font-semibold font-mono text-slate-300 mb-1">Neural Compiler Offline</h4>
                    <p className="text-xs max-w-sm font-sans">
                      Awaiting source code check. Run the <strong>Neural Compliance Check</strong> to query the server-side Gemini auditor and render compile audits.
                    </p>
                  </div>
                )}

                {/* Loading feedback animations */}
                {isAuditing && (
                  <div className="bg-[#161b22] border border-slate-800 rounded-xl p-8 flex flex-col items-center justify-center h-full min-h-[400px] text-center text-slate-500">
                    <RefreshCw className="h-12 w-12 text-emerald-500 mb-4 animate-spin" />
                    <h4 className="text-sm font-semibold font-mono text-white mb-2">Neural Security Audit In Progress</h4>
                    <div className="text-xs max-w-sm font-sans space-y-2">
                      <p className="text-emerald-400 font-mono animate-pulse">Running Borrow-Checker physical audits...</p>
                      <p className="text-slate-500 text-[10px]">Analyzing against Cargo Workspace Dependency direction regulations.</p>
                      <p className="text-slate-500 text-[10px]">Validating traces telemetry macro allocations.</p>
                    </div>
                  </div>
                )}

                {/* Actual structured report content loaded */}
                {auditResult && (
                  <motion.div
                    initial={{ opacity: 0, scale: 0.98 }}
                    animate={{ opacity: 1, scale: 1 }}
                    className="space-y-4"
                  >
                    
                    {/* Compliance KPI Card */}
                    <div className={`p-6 rounded-xl border relative overflow-hidden ${
                      auditResult.isCompliant
                        ? "bg-emerald-500/5 border-emerald-500/20"
                        : "bg-rose-500/5 border-rose-500/20"
                    }`}>
                      
                      <div className="flex items-center justify-between">
                        <div>
                          <span className="text-[10px] uppercase font-mono tracking-wider block text-slate-500">COMPILER BOUND STATUS</span>
                          <h3 className={`text-2xl font-black font-mono mt-1 ${
                            auditResult.isCompliant ? "text-emerald-400" : "text-rose-400"
                          }`}>
                            {auditResult.isCompliant ? "COMPLIANCE PASSED" : "VIOLATION DISCOVERED"}
                          </h3>
                        </div>

                        {/* Neural Score meter */}
                        <div className="text-right">
                          <span className="text-[9px] uppercase font-mono text-slate-505 block">COMPILER SCORE</span>
                          <span className={`text-4xl font-black font-mono leading-none ${
                            auditResult.isCompliant ? "text-emerald-400" : "text-amber-400"
                          }`}>
                            {auditResult.complianceScore}%
                          </span>
                        </div>
                      </div>

                      <div className="border-t border-slate-800 my-4" />

                      {/* Display specific violations */}
                      {auditResult.detectedViolations.length > 0 ? (
                        <div>
                          <span className="text-[10px] uppercase font-mono text-slate-500 tracking-wider block mb-2">Violated Rules Indexes:</span>
                          <div className="flex flex-wrap gap-1.5">
                            {auditResult.detectedViolations.map((v, i) => (
                              <span key={i} className="bg-rose-500/10 text-rose-300 font-mono text-[9px] px-2 py-0.5 rounded border border-rose-500/20">
                                {v}
                              </span>
                            ))}
                          </div>
                        </div>
                      ) : (
                        <div className="flex items-center space-x-2 text-emerald-400 text-xs font-mono font-bold">
                          <CheckCircle className="h-4 w-4" />
                          <span>Code snippet is safe. Ready to assemble into production crates!</span>
                        </div>
                      )}

                    </div>

                    {/* Step Recommendations Block */}
                    {auditResult.recommendations.length > 0 && (
                      <div className="bg-[#161b22] border border-slate-800 rounded-xl p-5">
                        <span className="text-[10px] uppercase font-mono text-slate-400 tracking-wider block mb-3">Audits Remediations</span>
                        
                        <ul className="space-y-2 text-xs text-slate-300 font-sans">
                          {auditResult.recommendations.map((rec, i) => (
                            <li key={i} className="flex items-start space-x-2 leading-relaxed">
                              <span className="bg-indigo-500/10 text-indigo-400 text-[10px] font-mono font-bold px-1.5 rounded h-5 flex items-center justify-center shrink-0">
                                {i + 1}
                              </span>
                              <span>{rec}</span>
                            </li>
                          ))}
                        </ul>
                      </div>
                    )}

                    {/* Gemini Commentary markdown section */}
                    <div className="bg-[#161b22] border border-slate-800 rounded-xl p-6">
                      <span className="text-[10px] uppercase font-mono text-slate-500 tracking-wider block mb-3">Architectural Analysis Report</span>
                      <div className="text-xs text-slate-400 leading-relaxed font-mono whitespace-pre-line bg-[#0d1117] p-4 rounded border border-slate-810">
                        {auditResult.feedback}
                      </div>
                    </div>

                    {/* Compliant code fix view */}
                    {auditResult.suggestedFix && (
                      <div className="bg-[#161b22] border border-slate-800 rounded-xl p-5">
                        <div className="flex items-center justify-between mb-3 border-b border-slate-800 pb-2">
                          <span className="text-[10px] uppercase font-mono text-emerald-400 font-bold">REMEDIATED COMPLIANT ALTERNATIVE</span>
                          
                          <button
                            onClick={() => copyToClipboard(auditResult.suggestedFix || "")}
                            className="bg-slate-800 hover:bg-slate-700 text-slate-300 text-[9px] font-mono py-1 px-2.5 rounded border border-slate-700 transition"
                          >
                            Copy Fix
                          </button>
                        </div>

                        <pre className="bg-[#0d1117] border border-slate-810 rounded p-4 text-[11px] font-mono leading-relaxed text-emerald-300 overflow-x-auto max-h-72">
                          <code>{auditResult.suggestedFix}</code>
                        </pre>
                      </div>
                    )}

                  </motion.div>
                )}

              </div>
            </motion.div>
          )}

          {/* TAB 6: OFFLINE MAP HUD SIMULATOR */}
          {activeTab === "map" && (
            <motion.div
              initial={{ opacity: 0, y: 15 }}
              animate={{ opacity: 1, y: 0 }}
              className="grid grid-cols-1 lg:grid-cols-12 gap-8"
              id="offline-map-hud-view"
            >
              {/* Interactive SVG Radar Map Canvas Left */}
              <div className="lg:col-span-8 flex flex-col space-y-4">
                <div className="bg-[#161b22] border border-slate-800 rounded-xl p-6 shadow-xl relative overflow-hidden">
                  <div className="flex items-center justify-between border-b border-slate-800 pb-3 mb-4">
                    <div>
                      <h3 className="text-sm font-bold text-slate-400 font-mono tracking-wider uppercase">MapLibre Offline Canvas Emulator</h3>
                      <p className="text-[11px] text-slate-500 font-mono">Click anywhere inside the radar matrix to mock position or trace hikes</p>
                    </div>
                    <div className="flex items-center space-x-2 text-xs font-mono">
                      <span className="text-slate-500">Zoom:</span>
                      <span className="bg-slate-800 px-2 py-0.5 rounded text-emerald-400 font-bold">{mapZoom}x</span>
                    </div>
                  </div>

                  {/* Fully reactive canvas styled graphic */}
                  <div className="relative w-full aspect-square md:aspect-[16/10] bg-[#0d1117] border border-slate-800/80 rounded-lg overflow-hidden flex items-center justify-center">
                    
                    <svg
                      className="absolute inset-0 w-full h-full cursor-crosshair select-none"
                      viewBox="0 0 400 250"
                      onClick={(e) => {
                        const rect = e.currentTarget.getBoundingClientRect();
                        const x = e.clientX - rect.left;
                        const y = e.clientY - rect.top;
                        
                        // Map 400x250 back to GPS coordinates in bounding space
                        const boundingLng = -122.4250 + (x / rect.width) * 0.0110;
                        const boundingLat = 37.7800 - (y / rect.height) * 0.0080;
                        
                        const targetPt = { lat: boundingLat, lng: boundingLng };
                        setMapCenter(targetPt);
                        setMapAltitude((prev) => Math.max(80.0, prev + (Math.random() - 0.5) * 8.0));
                        
                        // If recording: append to path
                        if (isMapRecording) {
                          setMapActiveTrack((prev) => [...prev, targetPt]);
                        }
                        
                        // Record server log of tile download
                        const randomTileX = Math.floor(Math.random() * 200 + 9100);
                        const randomTileY = Math.floor(Math.random() * 150 + 6100);
                        const tmsY = Math.pow(2, mapZoom) - 1 - randomTileY;
                        
                        const logTime = new Date().toTimeString().split(' ')[0];
                        setTileServerLogs((prev) => [
                          ...prev,
                          {
                            time: logTime,
                            type: "DEBUG",
                            message: `Query resolved: Z:${mapZoom} X:${randomTileX} Y:${randomTileY} => TMS SQLite row ${tmsY} served (90ms)`
                          }
                        ].slice(-7));
                      }}
                    >
                      {/* Grid Line patterns */}
                      <g stroke="#1F6FEB" strokeWidth="0.25" opacity="0.3">
                        <line x1="0" y1="25" x2="400" y2="25" />
                        <line x1="0" y1="50" x2="400" y2="50" />
                        <line x1="0" y1="75" x2="400" y2="75" />
                        <line x1="0" y1="100" x2="400" y2="100" />
                        <line x1="0" y1="125" x2="400" y2="125" />
                        <line x1="0" y1="150" x2="400" y2="150" />
                        <line x1="0" y1="175" x2="400" y2="175" />
                        <line x1="0" y1="200" x2="400" y2="200" />
                        <line x1="0" y1="225" x2="400" y2="225" />
                        
                        <line x1="40" y1="0" x2="40" y2="250" />
                        <line x1="80" y1="0" x2="80" y2="250" />
                        <line x1="120" y1="0" x2="120" y2="250" />
                        <line x1="160" y1="0" x2="160" y2="250" />
                        <line x1="200" y1="0" x2="200" y2="250" />
                        <line x1="240" y1="0" x2="240" y2="250" />
                        <line x1="280" y1="0" x2="280" y2="250" />
                        <line x1="320" y1="0" x2="320" y2="250" />
                        <line x1="360" y1="0" x2="360" y2="250" />
                      </g>

                      {/* Concentric laser rings (Tactical Topography contour simulation) */}
                      <g fill="none" stroke="#238636" strokeWidth="0.5" opacity="0.15">
                        <circle cx="200" cy="125" r="40" />
                        <circle cx="200" cy="125" r="80" strokeWidth="0.75" />
                        <circle cx="200" cy="125" r="120" />
                        <circle cx="200" cy="125" r="170" />
                        
                        <circle cx="80" cy="60" r="30" />
                        <circle cx="80" cy="60" r="50" />
                        <circle cx="310" cy="180" r="25" />
                        <circle cx="310" cy="180" r="45" />
                      </g>

                      {/* Render Historical paths in ice-blue (toggled by state) */}
                      {mapHistoryVisible && (
                        <g fill="none" stroke="#58a6ff" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" opacity="0.75">
                          {/* Trail A */}
                          <polyline points="20,120 75,90 140,110 210,75 220,50" />
                          <circle cx="20" cy="120" r="2.5" fill="#58a6ff" />
                          <circle cx="220" cy="50" r="2.5" fill="#58a6ff" />

                          {/* Trail B */}
                          <polyline points="100,230 145,180 200,205 285,175 350,140" />
                          <circle cx="100" cy="230" r="2.5" fill="#58a6ff" />
                          <circle cx="350" cy="140" r="2.5" fill="#58a6ff" />
                        </g>
                      )}

                      {/* Render Active path in neon warning orange */}
                      <polyline
                        fill="none"
                        stroke="#ff7b72"
                        strokeWidth="3"
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        points={mapActiveTrack.map((pt) => {
                          const pxX = ((pt.lng - (-122.4250)) / 0.0110) * 400;
                          const pxY = ((37.7800 - pt.lat) / 0.0080) * 250;
                          return `${pxX.toFixed(1)},${pxY.toFixed(1)}`;
                        }).join(" ")}
                      />

                      {/* Flashing current GPS core position beacon circle */}
                      <g>
                        {(() => {
                          const beaconX = ((mapCenter.lng - (-122.4250)) / 0.0110) * 400;
                          const beaconY = ((37.7800 - mapCenter.lat) / 0.0080) * 250;
                          return (
                            <>
                              <circle cx={beaconX} cy={beaconY} r="7" fill="none" stroke="#DA3633" strokeWidth="1.5" className="animate-ping" style={{ transformOrigin: `${beaconX}px ${beaconY}px` }} />
                              <circle cx={beaconX} cy={beaconY} r="4.5" fill="#DA3633" stroke="#FFFFFF" strokeWidth="1" />
                            </>
                          );
                        })()}
                      </g>
                    </svg>

                    {/* Reticle Overlayer indicators */}
                    <div className="absolute top-4 left-4 font-mono text-[9px] text-[#238636] bg-[#0D1117]/85 border border-[#238636]/20 px-2 py-1 rounded">
                      <span>RADAR GRID SCANNERS CONFIRMED</span>
                    </div>

                    <div className="absolute bottom-4 right-4 font-mono text-[9px] text-slate-500 bg-[#0D1117]/85 border border-slate-800 px-2 py-1 rounded flex items-center space-x-1.5">
                      <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
                      <span>OFFLINE LOCAL SOURCE</span>
                    </div>
                  </div>

                  {/* Operational controls bottom bar */}
                  <div className="grid grid-cols-1 md:grid-cols-3 gap-3 mt-4">
                    <button
                      onClick={() => {
                        setMapHistoryVisible(!mapHistoryVisible);
                        const logTime = new Date().toTimeString().split(' ')[0];
                        setTileServerLogs((prev) => [
                          ...prev,
                          {
                            time: logTime,
                            type: "INFO",
                            message: `SQLite query: Toggle historical tracks visible => ${!mapHistoryVisible}`
                          }
                        ].slice(-7));
                      }}
                      className={`font-mono text-xs p-2.5 rounded border transition-all ${
                        mapHistoryVisible
                          ? "bg-emerald-600/10 border-emerald-500 text-white"
                          : "bg-slate-850 border-slate-800 text-slate-400"
                      }`}
                    >
                      {mapHistoryVisible ? "Disable Historical Layer" : "Load Historical Trails"}
                    </button>

                    <button
                      onClick={() => {
                        const newRecording = !isMapRecording;
                        setIsMapRecording(newRecording);
                        if (newRecording) {
                          // start
                          setMapActiveTrack([mapCenter]);
                        }
                        const logTime = new Date().toTimeString().split(' ')[0];
                        setTileServerLogs((prev) => [
                          ...prev,
                          {
                            time: logTime,
                            type: "INFO",
                            message: newRecording ? "Telemetry collection loop STARTED on FFI database" : "Telemetry writing halted. EndTrack state committed."
                          }
                        ].slice(-7));
                      }}
                      className={`font-mono text-xs p-2.5 rounded border transition-all font-bold ${
                        isMapRecording
                          ? "bg-[#ff7b72]/15 border-[#ff7b72] text-[#ff7b72] animate-pulse"
                          : "bg-[#1f6feb]/15 border-[#1f6feb] text-[#c9d1d9]"
                      }`}
                    >
                      {isMapRecording ? "HALT LIVE TRACKING" : "ENGAGE ACTIVE GPS RECORD"}
                    </button>

                    <button
                      onClick={() => {
                        // random mock step forward
                        const stepLat = mapCenter.lat + 0.0006 + (Math.random() - 0.5) * 0.0002;
                        const stepLng = mapCenter.lng + 0.0008 + (Math.random() - 0.5) * 0.0002;
                        const nextStep = { lat: stepLat, lng: stepLng };
                        setMapCenter(nextStep);
                        setMapAltitude((prev) => Math.max(90.0, prev + (Math.random() - 0.5) * 10.0));
                        if (isMapRecording) {
                          setMapActiveTrack((prev) => [...prev, nextStep]);
                        }
                        const logTime = new Date().toTimeString().split(' ')[0];
                        setTileServerLogs((prev) => [
                          ...prev,
                          {
                            time: logTime,
                            type: "DEBUG",
                            message: `Simulated Step: Lat: ${stepLat.toFixed(5)}, lng: ${stepLng.toFixed(5)} written.`
                          }
                        ].slice(-7));
                      }}
                      className="font-mono text-xs p-2.5 rounded border border-slate-700 bg-slate-800 text-slate-300 hover:bg-slate-700 transition"
                    >
                      Mock Step Coordinate
                    </button>
                  </div>
                </div>
              </div>

              {/* Sidebar: HUD navigation readouts and SQLite server logs right */}
              <div className="lg:col-span-4 flex flex-col space-y-6">
                
                {/* HUD board diagnostics */}
                <div className="bg-[#161b22] border border-slate-800 rounded-xl p-6">
                  <span className="text-[10px] text-emerald-400 font-mono tracking-wider font-bold uppercase block bg-emerald-500/10 w-fit px-2 py-0.5 rounded mb-4">
                    SYSTEM DASH TELEMETRY
                  </span>
                  
                  <div className="space-y-4 font-mono">
                    <div>
                      <span className="text-[10px] text-slate-500 uppercase block">COORDINATE MOCK</span>
                      <div className="text-sm font-semibold text-white mt-0.5">{mapCenter.lat.toFixed(5)}°N , {Math.abs(mapCenter.lng).toFixed(5)}°W</div>
                    </div>

                    <div className="grid grid-cols-2 gap-4">
                      <div>
                        <span className="text-[10px] text-slate-500 uppercase block">GPS ALTITUDE</span>
                        <div className="text-sm font-semibold text-emerald-400 mt-0.5">{mapAltitude.toFixed(1)}m</div>
                      </div>
                      <div>
                        <span className="text-[10px] text-slate-500 uppercase block">ACTIVE SEGMENTS</span>
                        <div className="text-sm font-semibold text-white mt-0.5">{mapActiveTrack.length} points</div>
                      </div>
                    </div>

                    <div>
                      <span className="text-[10px] text-slate-500 uppercase block">INTEGRATED INTERFACE</span>
                      <div className="text-xs text-indigo-400 mt-1 uppercase">MapLibre SDK 10.2.0 + SQLite MBTiles</div>
                    </div>

                    <div className="border-t border-slate-800 pt-3 text-[10px] text-slate-400 font-sans leading-relaxed">
                      All GIS features conform to our Swiss Modern offgrid constitution, guaranteeing zero internet reliance, zero API token leak vulnerabilities, and robust physical battery priority.
                    </div>
                  </div>
                </div>

                {/* Local Tile Server terminal daemon lines */}
                <div className="bg-[#0b0e14] border border-slate-800 rounded-xl p-5 flex-1 flex flex-col h-[280px]">
                  <div className="flex items-center space-x-2 border-b border-slate-800 pb-2 mb-3">
                    <Terminal className="h-3.5 w-3.5 text-indigo-400" />
                    <span className="text-[10px] font-mono text-indigo-400 uppercase font-bold">Local GIS Tile Server Daemon Logs</span>
                  </div>

                  <div className="flex-1 overflow-y-auto space-y-1.5 font-mono text-[10px] leading-relaxed scrolling">
                    {tileServerLogs.map((log, idx) => (
                      <div key={idx} className="flex items-start space-x-1 border-b border-slate-900/40 pb-1 hover:bg-slate-800/10">
                        <span className="text-slate-500">[{log.time}]</span>
                        <span className={`font-semibold shrink-0 px-1 rounded ${
                          log.type === "DEBUG" ? "text-emerald-400 bg-emerald-500/5" : "text-indigo-400 bg-indigo-500/5"
                        }`}>
                          {log.type}
                        </span>
                        <span className="text-slate-300">{log.message}</span>
                      </div>
                    ))}
                  </div>

                  <div className="text-[9px] text-slate-500 font-mono mt-2 text-right">
                    Daemon listening actively on port :8085
                  </div>
                </div>

              </div>
            </motion.div>
          )}

        </div>

      </main>

      {/* Footer copyright */}
      <footer className="max-w-7xl mx-auto px-6 mt-16 pt-6 border-t border-slate-800/80 text-center font-mono text-[10px] text-slate-500 w-full flex flex-col md:flex-row md:items-center justify-between gap-4" id="app-footer">
        <div>
          <span>SOVEREIGN NOTES CONSTITUTION CORE &bull; ALL RIGHTS RESERVED</span>
        </div>
        <div className="flex space-x-4">
          <span className="hover:text-slate-400">Security Standard: AES-GCM/Argon2</span>
          <span>&middot;</span>
          <span className="hover:text-slate-400">Telemetry Engine: Tracing 0.1</span>
        </div>
      </footer>

    </div>
  );
}

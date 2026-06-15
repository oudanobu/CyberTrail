/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import express from "express";
import path from "path";
import { fileURLToPath } from "url";
import { GoogleGenAI, Type } from "@google/genai";
import dotenv from "dotenv";

// Load environment variables
dotenv.config();

// Fixes missing ES Module variables __dirname and __filename in Node-ESM environment
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const app = express();
const PORT = 3000;

// Setup JSON parsing middleware
app.use(express.json({ limit: "5mb" }));

// Lazy initializer for Google Gen AI client with appropriate User-Agent headers
let geminiClient: GoogleGenAI | null = null;

function getGeminiClient(): GoogleGenAI {
  if (!geminiClient) {
    const key = process.env.GEMINI_API_KEY;
    if (!key) {
      console.warn("WARNING: GEMINI_API_KEY environment variable is missing.");
    }
    geminiClient = new GoogleGenAI({
      apiKey: key || "",
      httpOptions: {
        headers: {
          "User-Agent": "aistudio-build",
        },
      },
    });
  }
  return geminiClient;
}

// REST Backend API endpoints
app.get("/api/health", (req, res) => {
  res.json({ status: "healthy", timestamp: new Date().toISOString() });
});

// Smart code-compliance validator using server-side Gemini 3.5 API
app.post("/api/audit-code", async (req, res) => {
  const { code } = req.body;

  if (!code || typeof code !== "string" || code.trim().length === 0) {
    return res.status(400).json({ error: "Code content is empty or invalid." });
  }

  // Fallback default audit in case of key absence so the application is completely failsafe
  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey || apiKey.includes("MY_GEMINI_API_KEY")) {
    return res.json({
      isCompliant: false,
      complianceScore: 50,
      detectedViolations: ["GEMINI_KEY_UNCONFIGURED"],
      recommendations: [
        "Please configure your real GEMINI_API_KEY in the Secrets settings of AI Studio to activate full smart neural analysis.",
        "Local parsing detected default structures but could not run full deep compiler borrow audits.",
      ],
      feedback: "### Local Passive Audit Report\n\nYour server is running in passive fallback mode. Please configure `GEMINI_API_KEY` to trigger deep CyberTrail architectural compliance mapping.",
      suggestedFix: "// Set up your GEMINI_API_KEY in the secrets tab to enable full AI-assisted audits!\n" + code,
    });
  }

  try {
    const ai = getGeminiClient();

    const systemPrompt = `You are the CyberTrail Architecture Compiler and compliance auditor.
Your job is to strictly analyze the submitted Rust code against the CyberTrail Offline Tactical Hiking System Constitution.

CyberTrail Key Constitution Rules:
1. Workspace Architecture: Decoupled crates (common, domain, application, database, sensors, tracking, altitude, navigation, rendering, infrastructure, ffi).
2. Dependency Law: UI -> Application -> Domain <- Infrastructure. Cyclic dependencies or sideways leaks (e.g. database querying UI, or ui directly accessing sqlx) are FORBIDDEN.
3. Domain Purity: Entities (Track, Anchor) & Repository traits. No Android or OS dependencies.
4. Application Coordinator: Handles use cases, NOT database storage directly.
5. Sensors/Altitude: Output agnostic SensorEvent. No tight Android LocationManager coupling.
6. Rendering: Canvas/OpenGL only. Google Maps is strictly forbidden.
7. Database: WAL, Foreign Keys, Prepared Statements mandatory.
8. FFI: Strict C-ABI boundaries (extern "C", #[repr(C)]). No direct memory leaks across to Android.
9. AI Output Contract: NO placeholders (todo!(), unimplemented!(), .unwrap() or .expect()). All statements must be fully implemented.

Analyze the user code. You must output the analysis results strictly structured as a JSON object with the following schema:
{
  "isCompliant": boolean,
  "complianceScore": number,
  "detectedViolations": string[], // matching constitution sub-topics
  "recommendations": string[], 
  "feedback": string,
  "suggestedFix": string 
}`;

    const response = await ai.models.generateContent({
      model: "gemini-3.5-flash",
      contents: `Audit this Rust code snippet:\n\n\`\`\`rust\n${code}\n\`\`\``,
      config: {
        systemInstruction: systemPrompt,
        responseMimeType: "application/json",
        responseSchema: {
          type: Type.OBJECT,
          required: ["isCompliant", "complianceScore", "detectedViolations", "recommendations", "feedback"],
          properties: {
            isCompliant: { type: Type.BOOLEAN },
            complianceScore: { type: Type.INTEGER },
            detectedViolations: {
              type: Type.ARRAY,
              items: { type: Type.STRING },
            },
            recommendations: {
              type: Type.ARRAY,
              items: { type: Type.STRING },
            },
            feedback: { type: Type.STRING },
            suggestedFix: { type: Type.STRING },
          },
        },
      },
    });

    const parsedResponse = JSON.parse(response.text || "{}");
    res.json(parsedResponse);
  } catch (error: any) {
    console.error("Gemini Code Audit Error:", error);
    res.status(500).json({
      error: "Failed to perform neural code compliance audit.",
      details: error.message || String(error),
    });
  }
});

// Configure Vite integration Based on Environment variables.
async function configureViteAndStatic() {
  if (process.env.NODE_ENV !== "production") {
    // Import Vite dynamically to prevent loading development modules in production container cold start
    const { createServer: createViteServer } = await import("vite");
    const viteInstance = await createViteServer({
      server: { middlewareMode: true },
      appType: "spa",
    });
    // Mount Vite dev server middlewares to serve React assets automatically
    app.use(viteInstance.middlewares);
    console.log("Environment: DEVELOPMENT. Running express with married Vite Dev middleware.");
  } else {
    // In production container environment, serve pre-compiled index.html and assets directly
    const distPath = path.join(process.cwd(), "dist");
    app.use(express.static(distPath));
    app.get("*", (req, res) => {
      res.sendFile(path.join(distPath, "index.html"));
    });
    console.log(`Environment: PRODUCTION. Serving assets from static path: ${distPath}`);
  }

  // Bind and start listening
  app.listen(PORT, "0.0.0.0", () => {
    console.log(`[CyberTrail Constitution] Dev server actively listening on http://localhost:${PORT}`);
  });
}

configureViteAndStatic().catch((err) => {
  console.error("Critical: Failed to initialize full-stack container server:", err);
  process.exit(1);
});

import { useState, useEffect } from 'react';
import { 
  Compass, 
  Activity, 
  HardDrive, 
  Map, 
  Wifi, 
  WifiOff, 
  Info, 
  Folder, 
  FolderPlus, 
  ChevronRight, 
  Download, 
  Check, 
  Trash2, 
  RefreshCw, 
  FileText, 
  Sliders,
  Database,
  Navigation,
  Zap,
  Footprints
} from 'lucide-react';

// Interfaces for Offline Architecture simulation
interface MapPackage {
  id: string;
  name: string;
  filename: string;
  size: string;
  bounds: string;
  latRange: [number, number];
  lonRange: [number, number];
  zooms: string;
  status: 'installed' | 'available' | 'downloading';
  progress?: number;
}

interface DirectoryItem {
  name: string;
  type: 'file' | 'folder';
  size?: string;
  items?: DirectoryItem[];
}

export default function App() {
  const [activeTab, setActiveTab] = useState<'index' | 'downloader' | 'directory' | 'gis' | 'ins' | 'architecture'>('ins');
  const [offlineMode, setOfflineMode] = useState<boolean>(true);

  // INS / PDR Simulator State (Phase 10)
  const [insNavState, setInsNavState] = useState<'NORMAL' | 'HYBRID' | 'INS_ONLY' | 'GPS_RECOVERY'>('NORMAL');
  const [insHeading, setInsHeading] = useState<number>(45.0); // Heading theta in degrees (45 deg = NE)
  const [insStepCount, setInsStepCount] = useState<number>(0);
  const [insStepLength, setInsStepLength] = useState<number>(0.72); // meters
  const [insLat, setInsLat] = useState<number>(40.12345);
  const [insLon, setInsLon] = useState<number>(124.38910);
  const [insDriftNorth, setInsDriftNorth] = useState<number>(0.0);
  const [insDriftEast, setInsDriftEast] = useState<number>(0.0);
  const [autoWalk, setAutoWalk] = useState<boolean>(false);

  // INS Track Log
  const [insTrackPoints, setInsTrackPoints] = useState<Array<{
    id: number;
    time: string;
    lat: number;
    lon: number;
    elev: number;
    slope: number;
    aspect: number;
    source: 'GPS' | 'INS' | 'GPS+INS';
    navState: string;
  }>>([
    {
      id: 1,
      time: new Date().toLocaleTimeString(),
      lat: 40.12345,
      lon: 124.38910,
      elev: 754.2,
      slope: 18.5,
      aspect: 135.0,
      source: 'GPS',
      navState: 'NORMAL'
    }
  ]);
  
  // Custom query parameters for Map Selection Rule-Selector Analyzer
  const [queryLat, setQueryLat] = useState<number>(40.123665);
  const [queryLon, setQueryLon] = useState<number>(124.389216);
  const [queryZoom, setQueryZoom] = useState<number>(13);
  const [selectedSource, setSelectedSource] = useState<string>('Dandong.mbtiles');
  const [routingLog, setRoutingLog] = useState<string[]>([]);

  // Simulation parameters for Offline GIS Terrain system
  const [gisLat, setGisLat] = useState<number>(40.12512);
  const [gisLon, setGisLon] = useState<number>(124.39145);
  const [simElev, setSimElev] = useState<number>(754.3);
  const [simSlope, setSimSlope] = useState<number>(18.5);
  const [simAspect, setSimAspect] = useState<number>(135.0);
  const [simHillshade, setSimHillshade] = useState<number>(198);
  const [isGisScanning, setIsGisScanning] = useState<boolean>(false);

  // DEM toggle to forbid simulation height when DEM files do not exist
  const [hasDemFiles, setHasDemFiles] = useState<boolean>(true);

  // Current active HUD telemetry source: 'DEM' | 'GPS' | 'SIMULATION' | 'NONE'
  const [hudTelemetrySource, setHudTelemetrySource] = useState<'DEM' | 'GPS' | 'SIMULATION' | 'NONE'>('DEM');

  // Diagnostic Logs Terminal
  const [terminalLogs, setTerminalLogs] = useState<string[]>([
    "[SYSTEM] CyberTrail Offgrid Navigation Engine initialized.",
    "[SYSTEM] Initializing unified storage path at /storage/emulated/0/CyberTrail/",
    "[SYSTEM] Loading Mapbox engine, mapbox.setConnected(true) mandated for loopback bypassing.",
    "[SERVER] LocalTileServer listening on 127.0.0.1:8080. Ready for XYZ tiles proxy.",
    "[DISCOVERY] Found 'world.mbtiles' in /storage/emulated/0/CyberTrail/Maps/",
    "[SYSTEM] High precision digital elevation models preseeded: Yosemite Peak (Z14 grid cells)."
  ]);

  // Directory Tree state
  const dirTree: DirectoryItem[] = [
    {
      name: "CyberTrail",
      type: "folder",
      items: [
        {
          name: "Maps",
          type: "folder",
          items: [
            { name: "world.mbtiles", type: "file", size: "9.6 MB" },
            { name: "Yosemite.mbtiles", type: "file", size: "8.2 GB" },
            { name: "Dandong.mbtiles", type: "file", size: "12.4 GB" }
          ]
        },
        {
          name: "DEM",
          type: "folder",
          items: [
            { name: "liaoning_srtm_3arc.hgt", type: "file", size: "125.4 MB" },
            { name: "yosemite_gdem_1arc.bil", type: "file", size: "82.1 MB" },
            { name: "dandong_spot_5m.tif", type: "file", size: "318.0 MB" }
          ]
        },
        {
          name: "Downloads",
          type: "folder",
          items: [
            { name: "Tokyo.mbtiles.download", type: "file", size: "1.2 GB" }
          ]
        },
        {
          name: "Routes",
          type: "folder",
          items: [
            { name: "Yalu_River_Scenic_Trail.gpx", type: "file", size: "1.4 MB" },
            { name: "Tiger_Mountain_Great_Wall.gpx", type: "file", size: "840 KB" }
          ]
        },
        {
          name: "Tracks",
          type: "folder",
          items: [
            { name: "Track_2026_06_20_1430.gpx", type: "file", size: "2.8 MB" }
          ]
        }
      ]
    }
  ];

  // Available map package states from JSON configurations
  const [mapPackages, setMapPackages] = useState<MapPackage[]>([
    {
      id: "world",
      name: "World Overview (全球基础底图)",
      filename: "world.mbtiles",
      size: "9.6 MB",
      bounds: "-180,-85,180,85",
      latRange: [-85, 85],
      lonRange: [-180, 180],
      zooms: "Z0 - Z6",
      status: "installed"
    },
    {
      id: "china",
      name: "China Overview (中国大陆中高比例影像)",
      filename: "China.mbtiles",
      size: "64.8 GB",
      bounds: "18.0,73.0,54.0,135.0",
      latRange: [18.0, 54.0],
      lonRange: [73.0, 135.0],
      zooms: "Z4 - Z9",
      status: "available"
    },
    {
      id: "liaoning",
      name: "Liaoning (辽宁全境高分辨率地形影像)",
      filename: "Liaoning.mbtiles",
      size: "24.1 GB",
      bounds: "38.5,118.5,43.5,126.5",
      latRange: [38.5, 43.5],
      lonRange: [118.5, 126.5],
      zooms: "Z8 - Z12",
      status: "available"
    },
    {
      id: "dandong",
      name: "Dandong (丹东高精度遥感测绘级大包)",
      filename: "Dandong.mbtiles",
      size: "12.4 GB",
      bounds: "39.8,123.8,40.6,124.8",
      latRange: [39.8, 40.6],
      lonRange: [123.8, 124.8],
      zooms: "Z12 - Z15",
      status: "installed"
    },
    {
      id: "tokyo",
      name: "Tokyo (东京核心都市离线测区瓦片)",
      filename: "Tokyo.mbtiles",
      size: "4.8 GB",
      bounds: "35.4,139.1,35.9,139.9",
      latRange: [35.4, 35.9],
      lonRange: [139.1, 139.9],
      zooms: "Z9 - Z15",
      status: "available"
    },
    {
      id: "japan",
      name: "Japan (日本中大比例影像混合底图)",
      filename: "Japan.mbtiles",
      size: "18.2 GB",
      bounds: "30.0,128.0,45.0,146.0",
      latRange: [30.0, 45.0],
      lonRange: [128.0, 146.0],
      zooms: "Z5 - Z10",
      status: "available"
    }
  ]);

  // Download simulation mimicking server download saving directly to /storage/emulated/0/CyberTrail/Maps/
  const startDownloadSimulator = (pkgId: string) => {
    setMapPackages(prev => prev.map(pkg => {
      if (pkg.id === pkgId) {
        return { ...pkg, status: 'downloading', progress: 0 };
      }
      return pkg;
    }));
    
    addLog(`[DOWNLOAD] Initiating official download request for ${pkgId}.mbtiles.`);
  };

  const downloadingPkgId = mapPackages.find(p => p.status === 'downloading')?.id || '';

  useEffect(() => {
    if (!downloadingPkgId) return;

    const interval = setInterval(() => {
      setMapPackages(prev => {
        let finishedPkgFilename = '';
        const next = prev.map(pkg => {
          if (pkg.id === downloadingPkgId) {
            const currentProgress = pkg.progress || 0;
            if (currentProgress >= 100) {
              finishedPkgFilename = pkg.filename;
              return { ...pkg, status: 'installed' as const, progress: undefined };
            }
            return { ...pkg, progress: currentProgress + 25 };
          }
          return pkg;
        });

        if (finishedPkgFilename) {
          setTimeout(() => {
            addLog(`[SYSTEM] Finished downloading ${finishedPkgFilename}. Saving directly to: /storage/emulated/0/CyberTrail/Maps/`);
            addLog(`[DISCOVERY] Saved and indexed new catalog of offline tiles structure from ${finishedPkgFilename}. Available instantly without APK restart.`);
          }, 0);
        }
        return next;
      });
    }, 600);

    return () => clearInterval(interval);
  }, [downloadingPkgId]);

  // Dynamic route resolution simulator for multi-map switching
  const runSourceSelector = (lat: number, lon: number, zoom: number) => {
    const logs: string[] = [];
    logs.push(`🔍 激活多数据源自动优配算法 - 查询坐标: (${lat.toFixed(5)}, ${lon.toFixed(5)}) 层级 Zoom: ${zoom}`);

    // Map priority filter
    const searchTarget = mapPackages.filter(p => p.status === 'installed');
    
    // Switch rule: Specifying priority logic: Dandong.mbtiles > Liaoning.mbtiles > China.mbtiles > World.mbtiles
    // Sort packages based on specificity of bounds (smaller bounds = more specific)
    const sortedPkgs = [...searchTarget].sort((a, b) => {
      // Manual priority override
      const priorityOrder = ['dandong', 'liaoning', 'china', 'world'];
      const indexA = priorityOrder.indexOf(a.id);
      const indexB = priorityOrder.indexOf(b.id);
      if (indexA !== -1 && indexB !== -1) {
        return indexA - indexB; // Prioritize Dandong, then Liaoning, then China, then World
      }
      
      const areaA = (a.latRange[1] - a.latRange[0]) * (a.lonRange[1] - a.lonRange[0]);
      const areaB = (b.latRange[1] - b.latRange[0]) * (b.lonRange[1] - b.lonRange[0]);
      return areaA - areaB; 
    });

    let matchedSource = 'world.mbtiles';
    let matchedReason = '未匹配到高精度局部遥感包，平滑向下回退至全球基础图库 [world.mbtiles]。';

    for (const pkg of sortedPkgs) {
      const isInsideLat = lat >= pkg.latRange[0] && lat <= pkg.latRange[1];
      const isInsideLon = lon >= pkg.lonRange[0] && lon <= pkg.lonRange[1];
      
      if (isInsideLat && isInsideLon) {
        logs.push(`✅ 判定: 命中坐标在 [${pkg.filename}] 的精确覆盖内 (${pkg.bounds})`);
        
        // Check levels
        const zoomsParts = pkg.zooms.split('-').map(z => parseInt(z.replace(/\D/g, '')));
        const minZ = zoomsParts[0];
        const maxZ = zoomsParts[1];
        
        if (zoom >= minZ && zoom <= maxZ) {
          matchedSource = pkg.filename;
          matchedReason = `坐标与请求级 Z${zoom} 符合瓦片策略。匹配最高优先级源: ${pkg.filename}`;
          break;
        } else {
          logs.push(`⚠️ 优先排除: [${pkg.filename}] 包匹配但请求 Zoom ${zoom} 超出其缩放层级 (${pkg.zooms})`);
        }
      } else {
        logs.push(`❌ 跳过: 坐标不属于 [${pkg.filename}] 包的测区网络。`);
      }
    }

    logs.push(`🎯 决策结果: ${matchedReason}`);
    setRoutingLog(logs);
    setSelectedSource(matchedSource);
  };

  const installedPkgIdsString = mapPackages
    .filter(p => p.status === 'installed')
    .map(p => p.id)
    .join(',');

  useEffect(() => {
    runSourceSelector(queryLat, queryLon, queryZoom);
  }, [queryLat, queryLon, queryZoom, installedPkgIdsString]);

  // INS / PDR Simulation Engine Functions
  const executePdrStep = () => {
    const headingRad = (insHeading * Math.PI) / 180.0;
    const deltaNorth = insStepLength * Math.cos(headingRad);
    const deltaEast = insStepLength * Math.sin(headingRad);

    const deltaLatDeg = (deltaNorth / 6378137.0) * (180.0 / Math.PI);
    const deltaLonDeg = (deltaEast / (6378137.0 * Math.cos(insLat * Math.PI / 180.0))) * (180.0 / Math.PI);

    const newLat = insLat + deltaLatDeg;
    const newLon = insLon + deltaLonDeg;
    
    setInsLat(newLat);
    setInsLon(newLon);
    setInsStepCount(prev => prev + 1);

    if (insNavState === 'INS_ONLY') {
      setInsDriftNorth(prev => prev + deltaNorth * 0.05);
      setInsDriftEast(prev => prev + deltaEast * 0.05);
    }

    // Re-query DEM terrain
    const x = (newLon + 122.4194) * 111000.0;
    const y = (newLat - 37.7749) * 111000.0;
    const wave1 = Math.sin(x / 3000.0) * Math.cos(y / 3000.0) * 820.0;
    const wave2 = Math.sin(x / 500.0) * Math.cos(y / 500.0) * 140.0;
    const calculatedElev = Math.abs(620.0 + wave1 + wave2);
    const calculatedSlope = Math.abs(Math.sin(y / 800.0) * 35.0) + Math.abs(Math.cos(x / 1400.0) * 12.0);
    const calculatedAspect = (Math.abs(x + y) * 13.5) % 360;

    const sourceLabel: 'GPS' | 'INS' | 'GPS+INS' = 
      insNavState === 'INS_ONLY' ? 'INS' :
      insNavState === 'HYBRID' || insNavState === 'GPS_RECOVERY' ? 'GPS+INS' : 'GPS';

    setInsTrackPoints(prev => [
      ...prev,
      {
        id: prev.length + 1,
        time: new Date().toLocaleTimeString(),
        lat: newLat,
        lon: newLon,
        elev: Number(calculatedElev.toFixed(1)),
        slope: Number(calculatedSlope.toFixed(1)),
        aspect: Number(calculatedAspect.toFixed(1)),
        source: sourceLabel,
        navState: insNavState
      }
    ]);

    addLog(`[INS_PDR] Step #${insStepCount + 1} (${insStepLength}m @ ${insHeading}°). PDR Lat=${newLat.toFixed(6)}, Lon=${newLon.toFixed(6)}. DEM Elev=${calculatedElev.toFixed(1)}m. Source: [${sourceLabel}]`);
  };

  const handleGpsRecovery = () => {
    setInsNavState('GPS_RECOVERY');
    addLog(`[KALMAN_FUSION] GPS signal re-acquired! Entering GPS_RECOVERY mode.`);
    addLog(`[KALMAN_FUSION] Fusing GPS measurement with INS accumulated drift: N=${insDriftNorth.toFixed(2)}m, E=${insDriftEast.toFixed(2)}m.`);

    setTimeout(() => {
      setInsDriftNorth(0.0);
      setInsDriftEast(0.0);
      setInsNavState('NORMAL');
      addLog(`[KALMAN_FUSION] Drift successfully corrected. NavState smoothly returned to NORMAL.`);
    }, 1200);
  };

  useEffect(() => {
    if (!autoWalk) return;
    const timer = setInterval(() => {
      executePdrStep();
    }, 800);
    return () => clearInterval(timer);
  }, [autoWalk, insHeading, insStepLength, insLat, insLon, insNavState, insStepCount]);

  // Offline GIS terrain analysis math
  const executeGisAnalysisOffgrid = () => {
    if (!hasDemFiles) {
      addLog(`[GIS_ENGINE] Cancelled: No DEM data files located in DEM/ directory.`);
      return;
    }
    setIsGisScanning(true);
    addLog(`[GIS_ENGINE] Initiating micro-topographic Horn calculation kernel offline...`);
    
    setTimeout(() => {
      // Offline calculation formula based on geographic coordinates
      const x = (gisLon + 122.4194) * 111000.0;
      const y = (gisLat - 37.7749) * 111000.0;
      
      const wave1 = Math.sin(x / 3000.0) * Math.cos(y / 3000.0) * 820.0;
      const wave2 = Math.sin(x / 500.0) * Math.cos(y / 500.0) * 140.0;
      const wave3 = Math.sin(x / 110.0) * Math.cos(y / 110.0) * 30.0;
      
      const calculatedElev = Math.abs(620.0 + wave1 + wave2 + wave3);
      
      // Calculate slopes
      const sY = Math.abs(Math.sin(y / 800.0) * 35.0);
      const sX = Math.abs(Math.cos(x / 1400.0) * 12.0);
      const calculatedSlope = sY + sX;
      
      // Aspect
      const calculatedAspect = (Math.abs(x + y) * 13.5) % 360;
      
      // Hillshade computation
      const toRad = (deg: number) => (deg * Math.PI) / 180.0;
      const zenithRad = toRad(45.0); // sun altitude
      const azimuthRad = toRad(315.0); // sun azimuth
      const slopeRad = toRad(calculatedSlope);
      const aspectRad = toRad(calculatedAspect);
      
      const illum = Math.cos(zenithRad) * Math.cos(slopeRad) + 
                    Math.sin(zenithRad) * Math.sin(slopeRad) * Math.cos(azimuthRad - aspectRad);
      const intensity = Math.round(Math.max(0, Math.min(1, illum)) * 255);

      setSimElev(calculatedElev);
      setSimSlope(calculatedSlope);
      setSimAspect(calculatedAspect);
      setSimHillshade(intensity);
      
      setIsGisScanning(false);
      addLog(`[GIS_ENGINE] Offline Horn calculations completed. Elev: ${calculatedElev.toFixed(1)}m, Slope: ${calculatedSlope.toFixed(1)}°, Hillshade rate: ${intensity}%`);
      addLog(`=== DEM Package Manager ===`);
      addLog(`GPS: Lat=${gisLat.toFixed(5)}, Lon=${gisLon.toFixed(5)}`);
      addLog(`Found DEM Files: liaoning_srtm_3arc.hgt, yosemite_gdem_1arc.bil, dandong_spot_5m.tif`);
      addLog(`Matched DEM: dandong_spot_5m.tif`);
      addLog(`Selection Reason: GPS inside BoundingBox`);
      addLog(`Current Loaded DEM: /storage/emulated/0/CyberTrail/DEM/dandong_spot_5m.tif`);
    }, 600);
  };

  const addLog = (msg: string) => {
    const time = new Date().toLocaleTimeString();
    setTerminalLogs(prev => [...prev, `[${time}] ${msg}`].slice(-24));
  };

  const getSlopeBadge = (deg: number) => {
    if (deg < 10) return { color: 'text-emerald-400 border-emerald-500/30 bg-emerald-500/10', text: '安全坡度 (0-10°)' };
    if (deg < 25) return { color: 'text-yellow-400 border-yellow-500/30 bg-yellow-500/10', text: '高度警惕 (10-25°)' };
    if (deg < 40) return { color: 'text-orange-400 border-orange-500/30 bg-orange-500/10', text: '陡位警戒 (25-40°)' };
    return { color: 'text-rose-400 border-rose-500/30 bg-rose-500/10', text: '极度危险 (>40°)' };
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 font-sans flex flex-col antialiased">
      {/* Target ID for focus testing */}
      <div id="cybertrail_root" className="hidden">CyberTrail Control Node</div>
      
      {/* Dynamic Status Bar */}
      <div className="bg-slate-900 border-b border-slate-800 text-xs px-6 py-2 flex items-center justify-between gap-4">
        <div className="flex items-center gap-2">
          <span className="h-2 w-2 rounded-full bg-emerald-400 animate-pulse"></span>
          <span className="text-slate-400 font-mono">CyberTrail Engine Node: <b className="text-emerald-400 font-bold">/storage/emulated/0/CyberTrail/</b></span>
        </div>
        <div className="flex items-center gap-4">
          <div className="flex items-center gap-2">
            <span className="text-slate-500">网路模式:</span>
            <button 
              id="btn_network_toggle"
              onClick={() => {
                setOfflineMode(!offlineMode);
                addLog(`[NETWORK] Network environment changed to ${!offlineMode ? "Offline (纯离线工作状态)" : "Online (在线网络联动状态)"}`);
              }}
              className={`flex items-center gap-1.5 px-2.5 py-0.5 rounded border text-[10px] font-bold transition-all ${
                offlineMode 
                ? 'bg-rose-500/10 text-rose-400 border-rose-500/30 hover:bg-rose-500/20' 
                : 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30 hover:bg-emerald-500/20'
              }`}
            >
              {offlineMode ? <WifiOff className="w-3.5 h-3.5" /> : <Wifi className="w-3.5 h-3.5" />}
              {offlineMode ? "极限重力深层断网" : "公网在线联通"}
            </button>
          </div>
          <span className="text-slate-500 font-mono">UTC 12:33:08</span>
        </div>
      </div>

      {/* Main Container */}
      <header className="px-6 py-5 bg-slate-900 border-b border-slate-800 shadow-md">
        <div className="max-w-7xl mx-auto flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <div className="p-2.5 bg-gradient-to-tr from-emerald-600 to-teal-500 text-slate-950 rounded-xl shadow-inner">
              <Compass className="w-6 h-6 stroke-[2]" />
            </div>
            <div>
              <div className="flex items-center gap-2 mb-0.5">
                <h1 className="text-xl font-bold tracking-tight text-white leading-none">CyberTrail 闭环测绘级离线底图引擎</h1>
                <span className="px-1.5 py-0.5 text-[9px] uppercase font-mono border border-slate-700 rounded bg-slate-950 text-emerald-400">v1.3-Prod</span>
              </div>
              <p className="text-xs text-slate-400">Unified Storage Managers • Offline Topography Solvers • Smart Multi-Source Tile Matcher</p>
            </div>
          </div>
          <div className="flex bg-slate-950 p-1 rounded-lg border border-slate-800">
            <button 
              id="tab_index"
              onClick={() => setActiveTab('index')} 
              className={`px-4 py-1.5 rounded-md text-xs font-semibold transition-all flex items-center gap-1.5 ${activeTab === 'index' ? 'bg-slate-800 text-white shadow' : 'text-slate-400 hover:text-slate-300'}`}
            >
              <Map className="w-3.5 h-3.5" />
              离线底图分流匹配
            </button>
            <button 
              id="tab_downloader"
              onClick={() => setActiveTab('downloader')} 
              className={`px-4 py-1.5 rounded-md text-xs font-semibold transition-all flex items-center gap-1.5 ${activeTab === 'downloader' ? 'bg-slate-800 text-white shadow' : 'text-slate-400 hover:text-slate-300'}`}
            >
              <Download className="w-3.5 h-3.5" />
              离线底图下载中心
            </button>
            <button 
              id="tab_directory"
              onClick={() => setActiveTab('directory')} 
              className={`px-4 py-1.5 rounded-md text-xs font-semibold transition-all flex items-center gap-1.5 ${activeTab === 'directory' ? 'bg-slate-800 text-white shadow' : 'text-slate-400 hover:text-slate-300'}`}
            >
              <Folder className="w-3.5 h-3.5" />
              统一外部存储树
            </button>
            <button 
              id="tab_gis"
              onClick={() => setActiveTab('gis')} 
              className={`px-4 py-1.5 rounded-md text-xs font-semibold transition-all flex items-center gap-1.5 ${activeTab === 'gis' ? 'bg-slate-800 text-white shadow' : 'text-slate-400 hover:text-slate-300'}`}
            >
              <Activity className="w-3.5 h-3.5" />
              独立 DEM 物理海拔
            </button>
            <button 
              id="tab_ins"
              onClick={() => setActiveTab('ins')} 
              className={`px-4 py-1.5 rounded-md text-xs font-semibold transition-all flex items-center gap-1.5 ${activeTab === 'ins' ? 'bg-slate-800 text-white shadow' : 'text-slate-400 hover:text-slate-300'}`}
            >
              <Navigation className="w-3.5 h-3.5 text-emerald-400" />
              INS / PDR 惯性导航
            </button>
            <button 
              id="tab_architecture"
              onClick={() => setActiveTab('architecture')} 
              className={`px-4 py-1.5 rounded-md text-xs font-semibold transition-all flex items-center gap-1.5 ${activeTab === 'architecture' ? 'bg-slate-800 text-white shadow' : 'text-slate-400 hover:text-slate-300'}`}
            >
              <Info className="w-3.5 h-3.5" />
              系统白皮书
            </button>
          </div>
        </div>
      </header>

      {/* Primary Layout Area */}
      <div className="flex-1 max-w-7xl w-full mx-auto p-6 grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Main Workspace Left/Center */}
        <main className="lg:col-span-2 space-y-6">
          {/* TAB 1: Auto Discovery and Selector Engine */}
          {activeTab === 'index' && (
            <div className="bg-slate-900 border border-slate-800 rounded-xl p-6 shadow-md space-y-6 transition-all duration-300">
              <div className="flex items-center justify-between border-b border-slate-800 pb-4">
                <div className="flex items-center gap-2">
                  <Sliders className="w-5 h-5 text-emerald-400" />
                  <h2 className="text-base font-bold text-white">多地图大包智能级自动回退判定器</h2>
                </div>
                <div className="px-2.5 py-1 rounded bg-emerald-500/10 border border-emerald-500/20 text-emerald-300 font-mono text-[10px]">
                  元数据热重载匹配
                </div>
              </div>

              <p className="text-xs text-slate-300 leading-relaxed">
                在存储目录 <code className="text-emerald-400 bg-slate-950 px-1 py-0.5 rounded">CyberTrail/Maps/</code> 中只要新增 MBTiles 离线包，本智能分发机制将在用户变更相机定位时，按照 <b>Dandong.mbtiles &gt; Liaoning.mbtiles &gt; China.mbtiles &gt; World.mbtiles</b> 优先级自适应完成无损过渡切换。
              </p>

              {/* Coordinates input workspace */}
              <div className="bg-slate-950 border border-slate-800/80 p-4 rounded-lg space-y-4">
                <div className="flex justify-between items-center pb-2 border-b border-slate-800">
                  <h3 className="text-xs font-semibold text-slate-400 uppercase tracking-wide">1. 实体断网终端测区跳转</h3>
                  <span className="text-[10px] text-emerald-400">点击以下预设热点进行算法压测:</span>
                </div>
                
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
                  <button 
                    id="preset_btn_dandong"
                    onClick={() => {
                      setQueryLat(40.123665);
                      setQueryLon(124.389216);
                      setQueryZoom(14);
                      addLog(`[GEOLOCATION] Mock jumped to Dandong focus area.`);
                    }}
                    className={`p-2 text-left rounded border transition text-xs ${queryLat === 40.123665 ? 'border-emerald-500 bg-emerald-500/5' : 'bg-slate-900 border-slate-800 hover:border-emerald-500/30'}`}
                  >
                    <span className="font-semibold block mb-0.5 text-white">辽宁丹东 (测向核心)</span>
                    <span className="font-mono text-[10px] text-slate-400">40.1236° / 124.3892°</span>
                  </button>
                  <button 
                    id="preset_btn_liaoning"
                    onClick={() => {
                      setQueryLat(41.9754);
                      setQueryLon(123.6421);
                      setQueryZoom(11);
                      addLog(`[GEOLOCATION] Mock jumped to Shenyang/Liaoning generic zone.`);
                    }}
                    className={`p-2 text-left rounded border transition text-xs ${queryLat === 41.9754 ? 'border-emerald-500 bg-emerald-500/5' : 'bg-slate-900 border-slate-800 hover:border-emerald-500/30'}`}
                  >
                    <span className="font-semibold block mb-0.5 text-white">沈阳 (全境中分辨率)</span>
                    <span className="font-mono text-[10px] text-slate-400">41.9754° / 123.6421°</span>
                  </button>
                  <button 
                    id="preset_btn_china"
                    onClick={() => {
                      setQueryLat(34.0522);
                      setQueryLon(118.2437);
                      setQueryZoom(6);
                      addLog(`[GEOLOCATION] Mock jumped to China Overview.`);
                    }}
                    className={`p-2 text-left rounded border transition text-xs ${queryLat === 34.0522 ? 'border-emerald-500 bg-emerald-500/5' : 'bg-slate-900 border-slate-800 hover:border-emerald-500/30'}`}
                  >
                    <span className="font-semibold block mb-0.5 text-white">华北平原 (全国底图)</span>
                    <span className="font-mono text-[10px] text-slate-400">34.0522° / 118.2437°</span>
                  </button>
                  <button 
                    id="preset_btn_tokyo"
                    onClick={() => {
                      setQueryLat(35.6762);
                      setQueryLon(139.6503);
                      setQueryZoom(13);
                      addLog(`[GEOLOCATION] Mock jumped to Tokyo specific imagery area.`);
                    }}
                    className={`p-2 text-left rounded border transition text-xs ${queryLat === 35.6762 ? 'border-emerald-500 bg-emerald-500/5' : 'bg-slate-900 border-slate-800 hover:border-emerald-500/30'}`}
                  >
                    <span className="font-semibold block mb-0.5 text-white">东京区 (海外高精覆盖)</span>
                    <span className="font-mono text-[10px] text-slate-400">35.6762° / 139.6503°</span>
                  </button>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-3 gap-4 pt-2">
                  <div>
                    <label className="text-slate-400 text-xs block mb-1">手动调测纬度 Latitude</label>
                    <input 
                      id="input_lat"
                      type="number" 
                      step="0.0001" 
                      value={queryLat} 
                      onChange={(e) => setQueryLat(parseFloat(e.target.value) || 0)}
                      className="w-full bg-slate-900 border border-slate-800 rounded px-2 py-1.5 text-sm font-mono text-white focus:outline-none focus:border-emerald-500"
                    />
                  </div>
                  <div>
                    <label className="text-slate-400 text-xs block mb-1">手动调测经度 Longitude</label>
                    <input 
                      id="input_lon"
                      type="number" 
                      step="0.0001" 
                      value={queryLon} 
                      onChange={(e) => setQueryLon(parseFloat(e.target.value) || 0)}
                      className="w-full bg-slate-900 border border-slate-800 rounded px-2 py-1.5 text-sm font-mono text-white focus:outline-none focus:border-emerald-500"
                    />
                  </div>
                  <div>
                    <label className="text-slate-400 text-xs block mb-1">加载环境缩放 Zoom Level (Z{queryZoom})</label>
                    <input 
                      id="input_zoom"
                      type="range" 
                      min="0" 
                      max="18" 
                      value={queryZoom} 
                      onChange={(e) => setQueryZoom(parseInt(e.target.value))}
                      className="w-full h-2 bg-slate-900 rounded-lg appearance-none cursor-pointer accent-emerald-400 py-4"
                    />
                  </div>
                </div>
              </div>

              {/* Selector Logic Tracing Output */}
              <div className="space-y-3">
                <h3 className="text-xs font-semibold text-slate-400 uppercase tracking-wide">2. 离线底图包自动切换规则匹配演算 Tracing</h3>
                <div className="bg-slate-950 border border-slate-850 rounded-lg overflow-hidden font-mono text-[11px] p-4 text-slate-200">
                  <div className="space-y-2">
                    {routingLog.map((log, index) => (
                      <div key={index} className="flex gap-2">
                        <span className="text-slate-500">[{index+1}]</span>
                        <span className={log.includes('✅') ? 'text-emerald-400' : log.includes('🎯') ? 'text-amber-300 font-bold' : log.includes('❌') ? 'text-slate-500' : 'text-slate-300'}>{log}</span>
                      </div>
                    ))}
                  </div>

                  <div className="mt-4 pt-4 border-t border-slate-850 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
                    <div>
                      <span className="text-slate-400 block text-xs">引擎决定实时吐出渲染的 MBTiles 包</span>
                      <span className="text-base font-bold text-white font-sans flex items-center gap-1.5">
                        <HardDrive className="w-4 h-4 text-amber-400" />
                        {selectedSource}
                      </span>
                    </div>

                    <div className="px-3 py-1.5 rounded-lg bg-slate-900 border border-slate-800 text-xs flex gap-2">
                      <span className="text-slate-400">切换规则:</span>
                      <span className="text-emerald-400 font-bold">局部高精度包优先检索覆盖</span>
                    </div>
                  </div>
                </div>
              </div>

              {/* MBTiles Metadata Manager */}
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <h3 className="text-xs font-semibold text-slate-400 uppercase tracking-wide">3. Maps/ 物理离线底图扫描列表</h3>
                  <span className="text-[10px] text-slate-500">自动扫描识别 *.mbtiles 的 SQLite 结构文件</span>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                  {mapPackages.filter(p => p.status === 'installed').map((pkg) => (
                    <div key={pkg.id} className="bg-slate-950 border border-slate-850 p-3 rounded-lg flex flex-col justify-between gap-2">
                      <div className="space-y-1">
                        <div className="flex justify-between items-center">
                          <span className="font-semibold text-white text-xs block truncate leading-none">{pkg.name}</span>
                          <span className="text-[10px] font-mono text-emerald-400 bg-emerald-500/10 border border-emerald-500/20 px-1 py-0.2 rounded">Installed</span>
                        </div>
                        <p className="text-[10px] text-slate-400 leading-none">文件名: <code className="text-slate-300 font-mono text-[9px]">{pkg.filename}</code></p>
                      </div>

                      <div className="grid grid-cols-3 gap-1 text-[10px] text-slate-400 border-t border-slate-850 pt-2 font-mono">
                        <div>
                          <span className="text-[9px] text-slate-500 block">最小层级</span>
                          <span className="text-white block font-semibold">{pkg.zooms.split(' - ')[0]}</span>
                        </div>
                        <div>
                          <span className="text-[9px] text-slate-500 block">最大层级</span>
                          <span className="text-white block font-semibold">{pkg.zooms.split(' - ')[1]}</span>
                        </div>
                        <div>
                          <span className="text-[9px] text-slate-500 block">物理容量</span>
                          <span className="text-slate-300 block font-semibold">{pkg.size}</span>
                        </div>
                      </div>

                      <div className="text-[9px] text-slate-500 font-mono bg-slate-900/60 p-1 rounded overflow-x-auto whitespace-nowrap">
                        Bounds: {pkg.bounds}
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          )}

          {/* TAB 2: Maps Downloading and Custom Importing */}
          {activeTab === 'downloader' && (
            <div className="bg-slate-900 border border-slate-800 rounded-xl p-6 shadow-md space-y-6 transition-all duration-300">
              <div className="flex items-center justify-between border-b border-slate-800 pb-4">
                <div className="flex items-center gap-2">
                  <Download className="w-5 h-5 text-emerald-400 animate-bounce" />
                  <h2 className="text-base font-bold text-white">内置官方离线底图包下载中心 (JSON Configured)</h2>
                </div>
                <div className="text-slate-400 text-xs">
                  自动拉取最新地图版本配置文件
                </div>
              </div>

              <div className="space-y-4">
                <div className="flex justify-between items-center">
                  <h3 className="text-xs font-semibold text-slate-400 uppercase tracking-wide">可下载并一键解包的官方库 (Download Pool)</h3>
                  <span className="text-[10px] text-slate-500">下载后自动保存至 /storage/emulated/0/CyberTrail/Maps/</span>
                </div>
                
                <div className="space-y-3">
                  {mapPackages.map((pkg) => (
                    <div key={pkg.id} className="bg-slate-950 border border-slate-850 rounded-xl p-4 flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 hover:border-slate-700 transition duration-155">
                      <div className="space-y-1 flex-1">
                        <div className="flex items-center gap-2 flex-wrap">
                          <span className="font-bold text-white text-sm">{pkg.name}</span>
                          <span className="px-1.5 py-0.5 bg-slate-900 text-slate-400 rounded text-[9px] font-mono border border-slate-800">{pkg.size}</span>
                        </div>
                        <div className="text-xs text-slate-400 space-y-0.5">
                          <p>文件载体: <code className="text-slate-300 font-mono">{pkg.filename}</code></p>
                          <p>覆盖经纬极区: <span className="text-slate-300">{pkg.bounds}</span></p>
                          <p>允许断网渲染瓦片: <span className="text-amber-400 font-mono">{pkg.zooms}</span></p>
                        </div>
                      </div>

                      <div className="w-full sm:w-auto flex flex-col items-stretch sm:items-end gap-2">
                        {pkg.status === 'installed' && (
                          <div className="flex items-center gap-2">
                            <span className="px-2.5 py-1 rounded bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 text-xs font-bold flex items-center gap-1">
                              <Check className="w-3.5 h-3.5" /> 已在 Maps/ 中就绪
                            </span>
                            <button 
                              onClick={() => {
                                setMapPackages(prev => prev.map(p => p.id === pkg.id ? { ...p, status: 'available' } : p));
                                addLog(`[DELETE] Deleted ${pkg.filename} from /storage/emulated/0/CyberTrail/Maps/`);
                              }}
                              className="p-1.5 rounded hover:bg-rose-500/10 text-slate-500 hover:text-rose-400 transition"
                              title="物理删除此包"
                            >
                              <Trash2 className="w-4 h-4" />
                            </button>
                          </div>
                        )}

                        {pkg.status === 'available' && (
                          <button 
                            id={`download_btn_${pkg.id}`}
                            onClick={() => startDownloadSimulator(pkg.id)}
                            className="px-3.5 py-1.5 rounded-lg bg-emerald-500 hover:bg-emerald-600 font-semibold text-xs text-slate-950 text-center transition flex items-center justify-center gap-1"
                          >
                            <Download className="w-3.5 h-3.5 text-slate-950" />
                            一键极速下载
                          </button>
                        )}

                        {pkg.status === 'downloading' && (
                          <div className="w-full sm:w-36 space-y-1">
                            <span className="text-[10px] text-emerald-400 font-mono block text-right font-bold">后台多线程写入: {pkg.progress}%</span>
                            <div className="w-full bg-slate-900 h-1.5 rounded-full overflow-hidden border border-slate-850">
                              <div className="bg-emerald-400 h-full transition-all duration-300" style={{ width: `${pkg.progress}%` }}></div>
                            </div>
                          </div>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              {/* Custom Import Demonstration Panel */}
              <div className="border border-slate-800 rounded-xl p-5 bg-slate-950/20 space-y-4">
                <div className="text-xs font-bold text-white flex items-center gap-1.5">
                  <FolderPlus className="w-4 h-4 text-emerald-400" />
                  <span>自主拖拽导入外部存储包 (Tiger Mountain Case)</span>
                </div>
                
                <p className="text-xs text-slate-400 leading-relaxed">
                  CyberTrail 完全支持登山爱好者从外部下载的开源 MBTiles 卫星包。只需要通过本区域模拟拖入外部离线文件，APP 文件系统便可以直接获取 metadata 的 sqlite 索引机制，快速实现多级无缝匹配。
                </p>

                <div 
                  onClick={() => {
                    addLog(`[IMPORTER] Scanning external file path Tiger_Mountain_3D.mbtiles.`);
                    addLog(`[IMPORTER] Verifying SQLite structures & metadata... SUCCESS.`);
                    addLog(`[IMPORTER] Auto-moving to /storage/emulated/0/CyberTrail/Maps/. Fully indexed.`);
                  }}
                  className="border border-dashed border-slate-800 rounded-lg p-6 bg-slate-950/50 flex flex-col items-center justify-center text-center gap-2 hover:border-emerald-500/30 transition cursor-pointer"
                >
                  <FileText className="w-8 h-8 text-slate-600 mb-1" />
                  <span className="text-xs text-slate-300 font-semibold">模拟导入外部测试包</span>
                  <span className="text-[10px] text-slate-500">点击自动模拟导入: tiger_mountain_z16.mbtiles (3.2 GB)</span>
                  
                  <span className="mt-2 px-3 py-1 bg-slate-900 border border-slate-800 hover:border-emerald-500/30 text-[10px] text-slate-300 rounded hover:text-white transition">
                    🚀 执行一键无APK重新安装热倒入
                  </span>
                </div>
              </div>
            </div>
          )}

          {/* TAB 3: Directory Tree visualizer */}
          {activeTab === 'directory' && (
            <div className="bg-slate-900 border border-slate-800 rounded-xl p-6 shadow-md space-y-6 transition-all duration-300">
              <div className="flex items-center justify-between border-b border-slate-800 pb-4">
                <div className="flex items-center gap-2">
                  <Folder className="w-5 h-5 text-emerald-400" />
                  <h2 className="text-base font-bold text-white">统一文件根存储目录 (/storage/emulated/0/CyberTrail/)</h2>
                </div>
                <div className="px-2 py-0.5 bg-rose-500/10 border border-rose-500/30 text-rose-400 rounded text-[9px] font-mono">
                  绕开 system/data 频繁清除问题
                </div>
              </div>

              <div className="text-xs text-slate-300 leading-relaxed space-y-3">
                <p>
                  CyberTrail 极其重视数据的完整性。原先由于将地图数据保存在包名包裹的局部缓存层，在进行系统升级或卸载 APK 时，数百 GB 的高精底图、等高线瓦片和轨迹数据都会付之一炬。
                </p>
                <p>
                  新版离线底图引擎重构了物理存储，强制启用统一外部公开路径 <b>/storage/emulated/0/CyberTrail/</b> 以及 <b>Maps/、DEM/、Downloads/</b>，并引入了以下清晰的可视化本地层级（完全对应 Android 外部存储文件关系树）：
                </p>
              </div>

              {/* Folder structure mapping */}
              <div className="bg-slate-950 border border-slate-850 rounded-xl p-5 space-y-3">
                <div className="text-xs font-semibold text-slate-400 pb-2 border-b border-slate-850">
                  统一存储空间拓扑 (Storage Emulated Roots)
                </div>
                
                {/* Recursive File Tree Visualizer */}
                <div className="font-mono text-xs text-slate-300 space-y-2.5">
                  <div className="flex items-center gap-2 font-bold text-emerald-400">
                    <Folder className="w-4 h-4" />
                    <span>/storage/emulated/0/</span>
                  </div>
                  
                  <div className="pl-6 space-y-2.5 border-l border-slate-850">
                    {dirTree.map((topNode, index) => (
                      <div key={index} className="space-y-2.5">
                        <div className="flex items-center gap-2 font-bold text-white">
                          <Folder className="w-4 h-4 text-amber-500" />
                          <span>{topNode.name}/</span>
                          <span className="text-[10px] text-slate-500">(物理统管根目录)</span>
                        </div>

                        {topNode.items && (
                          <div className="pl-6 space-y-2 border-l border-slate-850">
                            {topNode.items.map((subNode, subIdx) => (
                              <div key={subIdx} className="space-y-1.5">
                                <div className="flex items-center gap-2 text-slate-200 font-semibold">
                                  <Folder className="w-3.5 h-3.5 text-sky-400" />
                                  <span>{subNode.name}/</span>
                                </div>

                                {subNode.items && (
                                  <div className="pl-5 space-y-1">
                                    {subNode.items.map((leaf, leafIdx) => (
                                      <div key={leafIdx} className="flex items-center justify-between hover:bg-slate-900/40 p-1 rounded transition duration-150">
                                        <div className="flex items-center gap-2 text-slate-400">
                                          <ChevronRight className="w-3 h-3 text-slate-600" />
                                          <FileText className="w-3.5 h-3.5 text-slate-500" />
                                          <span>{leaf.name}</span>
                                        </div>
                                        <span className="text-[10px] text-slate-500 font-mono">{leaf.size}</span>
                                      </div>
                                    ))}
                                  </div>
                                )}
                              </div>
                            ))}
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* TAB 4: Pure Offline Elevation Subsystem */}
          {activeTab === 'gis' && (
            <div className="bg-slate-900 border border-slate-800 rounded-xl p-6 shadow-md space-y-6 transition-all duration-300">
              <div className="flex items-center justify-between border-b border-slate-800 pb-4">
                <div className="flex items-center gap-2">
                  <Activity className="w-5 h-5 text-emerald-400" />
                  <h2 className="text-base font-bold text-white">统一地形高程 DEM 解算模拟平台</h2>
                </div>
                <div className="flex items-center gap-2">
                  <span className="text-xs text-slate-400">本地 DEM 文件就绪:</span>
                  <button 
                    id="dem_toggle_switch"
                    onClick={() => {
                      setHasDemFiles(!hasDemFiles);
                      addLog(`[DEM] File existence status changed. Offline files mock present: ${!hasDemFiles}`);
                    }}
                    className={`px-3 py-1 rounded text-[10px] font-bold border transition ${
                      hasDemFiles 
                        ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20' 
                        : 'bg-rose-500/10 text-rose-400 border-rose-500/20'
                    }`}
                  >
                    {hasDemFiles ? "DEM就绪 (SRTM/TIF)" : "完全无 DEM 数据"}
                  </button>
                </div>
              </div>

              <div className="text-xs text-slate-300 leading-relaxed space-y-2">
                <p>
                  CyberTrail 断网时为了保障探险家的人身安全，对于地形剖面高程支持读取本地 <code className="text-teal-400 bg-slate-950 px-1 py-0.5 rounded">CyberTrail/DEM/</code> 目录下的 SRTM (HGT)、ASTER GDEM (BIL) 与 GeoTIFF (TIF) 数据。
                </p>
                <p className="text-rose-400 font-semibold border-l-2 border-rose-500 pl-2 bg-rose-950/20 py-1">
                  安全规范：如果没有本地 DEM 文件，系统将彻底禁止使用“模拟海拔”（避免欺骗并误导户外使用者），HUD 直接显示为“海拔: 无DEM数据”以提示不可用。
                </p>
              </div>

              {/* Topographic controls */}
              <div className="bg-slate-950 border border-slate-850 p-5 rounded-xl grid grid-cols-1 md:grid-cols-2 gap-6">
                <div className="space-y-4">
                  <div className="flex justify-between items-center pb-2 border-b border-slate-800">
                    <h3 className="text-xs font-bold text-white uppercase tracking-wider">测绘测点坐标滑块</h3>
                    <span className="px-1 py-0.2 text-[9px] bg-slate-900 rounded font-mono text-slate-400">HGT 3x3 Matrix</span>
                  </div>
                  
                  <div className="space-y-4">
                    <div>
                      <div className="flex justify-between text-xs text-slate-400 mb-1">
                        <span>测试纬度 Latitude</span>
                        <span className="font-mono text-white">{gisLat.toFixed(5)}°</span>
                      </div>
                      <input 
                        id="dem_slider_lat"
                        type="range" 
                        min="40.0" 
                        max="41.5" 
                        step="0.001" 
                        value={gisLat} 
                        onChange={(e) => setGisLat(parseFloat(e.target.value))}
                        className="w-full h-1.5 bg-slate-900 rounded-lg appearance-none cursor-pointer accent-emerald-400"
                      />
                    </div>

                    <div>
                      <div className="flex justify-between text-xs text-slate-400 mb-1">
                        <span>测试经度 Longitude</span>
                        <span className="font-mono text-white">{gisLon.toFixed(5)}°</span>
                      </div>
                      <input 
                        id="dem_slider_lon"
                        type="range" 
                        min="124.0" 
                        max="125.5" 
                        step="0.001" 
                        value={gisLon} 
                        onChange={(e) => setGisLon(parseFloat(e.target.value))}
                        className="w-full h-1.5 bg-slate-900 rounded-lg appearance-none cursor-pointer accent-emerald-400"
                      />
                    </div>

                    <div className="pt-2">
                      <button 
                        id="btn_execute_gis"
                        onClick={executeGisAnalysisOffgrid}
                        disabled={isGisScanning}
                        className="w-full py-2.5 rounded-lg bg-emerald-500 hover:bg-emerald-600 font-semibold text-xs text-slate-950 transition flex items-center justify-center gap-2"
                      >
                        <RefreshCw className={`w-4 h-4 ${isGisScanning ? 'animate-spin' : ''}`} />
                        {isGisScanning ? "正在读取 HGT/GeoTIFF 网格..." : "运算微地形差分指标 (Horn Kernel)"}
                      </button>
                    </div>
                  </div>
                </div>

                {/* Dashboard feedback results */}
                <div className="bg-slate-900 border border-slate-800 rounded-lg p-4 flex flex-col justify-between">
                  <div>
                    <span className="text-slate-400 text-xs block mb-3">Horn Algorithm GIS 物理运算输出</span>
                    
                    <div className="space-y-3.5 text-slate-200 text-sm">
                      <div className="flex justify-between items-center bg-slate-950 p-2 rounded">
                        <span>实体海拔高度 (Elevation):</span>
                        {hasDemFiles ? (
                          <span className="font-mono font-bold text-amber-400 text-base">{simElev.toFixed(1)} meters</span>
                        ) : (
                          <span className="font-mono font-bold text-rose-400 text-xs uppercase bg-rose-500/10 px-2 py-0.5 rounded border border-rose-500/20">无DEM数据</span>
                        )}
                      </div>

                      <div className="flex justify-between items-center bg-slate-950 p-2 rounded">
                        <span>3D 测算坡度 (Slope):</span>
                        {hasDemFiles ? (
                          <div className="flex items-center gap-2">
                            <span className="font-mono font-bold text-white text-base">{simSlope.toFixed(1)}°</span>
                            <span className={`px-2 py-0.5 rounded border text-[10px] font-bold ${getSlopeBadge(simSlope).color}`}>
                              {getSlopeBadge(simSlope).text}
                            </span>
                          </div>
                        ) : (
                          <span className="text-slate-500 font-mono text-sm font-bold">--</span>
                        )}
                      </div>

                      <div className="flex justify-between items-center bg-slate-950 p-2 rounded">
                        <span>差分坡向方位 (Aspect):</span>
                        {hasDemFiles ? (
                          <span className="font-mono font-semibold text-teal-400">{simAspect.toFixed(1)}° 南/东向</span>
                        ) : (
                          <span className="text-slate-500 font-mono text-sm font-bold">--</span>
                        )}
                      </div>

                      <div className="flex justify-between items-center bg-slate-950 p-2 rounded">
                        <span>光折阴影率 (Hillshade):</span>
                        {hasDemFiles ? (
                          <span className="font-mono font-semibold text-slate-300">{simHillshade} / 255</span>
                        ) : (
                          <span className="text-slate-500 font-mono text-sm font-bold">--</span>
                        )}
                      </div>

                      <div className="flex justify-between items-center bg-slate-950 p-2 rounded">
                        <span>数据来源定位指示:</span>
                        <span className={`font-mono text-xs font-bold px-2 py-0.5 rounded ${hasDemFiles ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20' : 'bg-rose-500/10 text-rose-400 border border-rose-500/20'}`}>
                          {hasDemFiles ? "数据来源: DEM" : "数据来源: - (禁止模拟)"}
                        </span>
                      </div>
                    </div>
                  </div>

                  {/* Compass Display Compass */}
                  <div className="mt-4 pt-3 border-t border-slate-800 flex justify-center">
                    <div className="relative h-20 w-20 rounded-full border border-slate-700 bg-slate-950 flex items-center justify-center">
                      <span className="absolute top-1 text-[8px] font-bold text-rose-500">N</span>
                      <span className="absolute bottom-1 text-[8px] font-bold text-slate-500">S</span>
                      <span className="absolute right-1 text-[8px] font-bold text-slate-500">E</span>
                      <span className="absolute left-1 text-[8px] font-bold text-slate-500">W</span>
                      
                      {/* Interactive needle */}
                      <div 
                        className="h-10 w-1 bg-gradient-to-t from-slate-700 to-amber-400 origin-bottom rounded transition-transform duration-500"
                        style={{ transform: `rotate(${hasDemFiles ? simAspect : 0}deg)`, transformOrigin: 'bottom' }}
                      />
                    </div>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* TAB 5: INS / PDR CyberTrail Inertial Navigation System */}
          {activeTab === 'ins' && (
            <div className="bg-slate-900 border border-slate-800 rounded-xl p-6 shadow-md space-y-6 transition-all duration-300">
              <div className="flex items-center justify-between border-b border-slate-800 pb-4">
                <div className="flex items-center gap-2">
                  <Navigation className="w-5 h-5 text-emerald-400" />
                  <h2 className="text-base font-bold text-white">Phase 10：CyberTrail 惯性导航与 PDR 步频推算系统</h2>
                </div>
                <div className="px-2.5 py-1 rounded bg-emerald-500/10 border border-emerald-500/20 text-emerald-300 font-mono text-[10px]">
                  GNSS + PDR + Kalman Fusion
                </div>
              </div>

              {/* Navigation State Controller */}
              <div className="bg-slate-950 border border-slate-800 p-4 rounded-xl space-y-3">
                <div className="flex justify-between items-center">
                  <span className="text-xs font-semibold text-slate-400 uppercase tracking-wide">导航状态切换器 (NavState Controller)</span>
                  <div className="flex items-center gap-2">
                    <span className="text-xs text-slate-400">当前导航状态:</span>
                    <span className={`px-2.5 py-1 rounded-md text-xs font-mono font-bold uppercase border ${
                      insNavState === 'NORMAL' ? 'bg-emerald-500/20 text-emerald-400 border-emerald-500/40' :
                      insNavState === 'HYBRID' ? 'bg-cyan-500/20 text-cyan-400 border-cyan-500/40' :
                      insNavState === 'INS_ONLY' ? 'bg-amber-500/20 text-amber-400 border-amber-500/40 animate-pulse' :
                      'bg-teal-500/20 text-teal-400 border-teal-500/40'
                    }`}>
                      {insNavState}
                    </span>
                  </div>
                </div>

                <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 pt-1">
                  <button
                    id="btn_state_normal"
                    onClick={() => {
                      setInsNavState('NORMAL');
                      addLog(`[NAV_STATE] Switch to NORMAL: GPS accuracy <= 15m.`);
                    }}
                    className={`p-2 rounded-lg border text-left text-xs font-medium transition ${insNavState === 'NORMAL' ? 'border-emerald-500 bg-emerald-500/10 text-white' : 'border-slate-800 bg-slate-900 text-slate-400 hover:text-white'}`}
                  >
                    <div className="font-bold text-emerald-400">NORMAL</div>
                    <div className="text-[10px] text-slate-400">GPS 信号良好</div>
                  </button>

                  <button
                    id="btn_state_hybrid"
                    onClick={() => {
                      setInsNavState('HYBRID');
                      addLog(`[NAV_STATE] Switch to HYBRID: Fusing GPS + IMU PDR.`);
                    }}
                    className={`p-2 rounded-lg border text-left text-xs font-medium transition ${insNavState === 'HYBRID' ? 'border-cyan-500 bg-cyan-500/10 text-white' : 'border-slate-800 bg-slate-900 text-slate-400 hover:text-white'}`}
                  >
                    <div className="font-bold text-cyan-400">HYBRID</div>
                    <div className="text-[10px] text-slate-400">GPS + IMU 混合导航</div>
                  </button>

                  <button
                    id="btn_state_ins_only"
                    onClick={() => {
                      setInsNavState('INS_ONLY');
                      addLog(`[NAV_STATE] Switch to INS_ONLY: GPS signal lost (>4s). PDR dead reckoning active!`);
                    }}
                    className={`p-2 rounded-lg border text-left text-xs font-medium transition ${insNavState === 'INS_ONLY' ? 'border-amber-500 bg-amber-500/10 text-white' : 'border-slate-800 bg-slate-900 text-slate-400 hover:text-white'}`}
                  >
                    <div className="font-bold text-amber-400">INS_ONLY</div>
                    <div className="text-[10px] text-slate-400">GPS 完全丢失 (断网/山洞)</div>
                  </button>

                  <button
                    id="btn_state_recovery"
                    onClick={handleGpsRecovery}
                    className={`p-2 rounded-lg border text-left text-xs font-medium transition ${insNavState === 'GPS_RECOVERY' ? 'border-teal-500 bg-teal-500/10 text-white' : 'border-slate-800 bg-slate-900 text-slate-400 hover:text-white'}`}
                  >
                    <div className="font-bold text-teal-400">GPS_RECOVERY</div>
                    <div className="text-[10px] text-slate-400">GPS 恢复 & Kalman 校正</div>
                  </button>
                </div>
              </div>

              {/* PDR Step & Orientation Controls */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div className="bg-slate-950 border border-slate-800 p-4 rounded-xl space-y-4">
                  <div className="flex items-center justify-between border-b border-slate-800 pb-2">
                    <span className="text-xs font-semibold text-slate-300 flex items-center gap-1.5">
                      <Footprints className="w-4 h-4 text-emerald-400" />
                      步长 & 航向推算配置
                    </span>
                    <span className="text-xs font-mono text-emerald-400">Step #{insStepCount}</span>
                  </div>

                  <div>
                    <div className="flex justify-between text-xs text-slate-400 mb-1">
                      <span>航向角 (Heading θ):</span>
                      <span className="font-mono text-white font-bold">{insHeading}° ({
                        insHeading >= 337.5 || insHeading < 22.5 ? "N 正北" :
                        insHeading < 67.5 ? "NE 东北" :
                        insHeading < 112.5 ? "E 正东" :
                        insHeading < 157.5 ? "SE 东南" :
                        insHeading < 202.5 ? "S 正南" :
                        insHeading < 247.5 ? "SW 西南" :
                        insHeading < 292.5 ? "W 正西" : "NW 西北"
                      })</span>
                    </div>
                    <input 
                      id="ins_slider_heading"
                      type="range" 
                      min="0" 
                      max="359" 
                      value={insHeading} 
                      onChange={(e) => setInsHeading(parseInt(e.target.value))}
                      className="w-full h-1.5 bg-slate-900 rounded-lg appearance-none cursor-pointer accent-emerald-400"
                    />
                  </div>

                  <div>
                    <div className="flex justify-between text-xs text-slate-400 mb-1">
                      <span>Weinberg 步长估计 (Step Length):</span>
                      <span className="font-mono text-white font-bold">{insStepLength.toFixed(2)} meters</span>
                    </div>
                    <input 
                      id="ins_slider_step_length"
                      type="range" 
                      min="0.40" 
                      max="1.20" 
                      step="0.02" 
                      value={insStepLength} 
                      onChange={(e) => setInsStepLength(parseFloat(e.target.value))}
                      className="w-full h-1.5 bg-slate-900 rounded-lg appearance-none cursor-pointer accent-emerald-400"
                    />
                  </div>

                  <div className="flex gap-2 pt-2">
                    <button
                      id="btn_pdr_step"
                      onClick={executePdrStep}
                      className="flex-1 py-2.5 rounded-lg bg-emerald-500 hover:bg-emerald-600 text-slate-950 font-bold text-xs transition flex items-center justify-center gap-1.5"
                    >
                      <Footprints className="w-4 h-4" />
                      迈步一次 (Single Step)
                    </button>

                    <button
                      id="btn_auto_walk"
                      onClick={() => setAutoWalk(!autoWalk)}
                      className={`px-4 py-2.5 rounded-lg font-bold text-xs transition flex items-center justify-center gap-1.5 border ${
                        autoWalk 
                          ? 'bg-rose-500/20 text-rose-400 border-rose-500/40 animate-pulse' 
                          : 'bg-slate-800 text-slate-200 border-slate-700 hover:bg-slate-700'
                      }`}
                    >
                      <Zap className="w-4 h-4" />
                      {autoWalk ? "停止步行" : "自动连续步行"}
                    </button>
                  </div>
                </div>

                {/* IMU Sensors Real-time Readings */}
                <div className="bg-slate-950 border border-slate-800 p-4 rounded-xl space-y-3">
                  <div className="flex items-center justify-between border-b border-slate-800 pb-2">
                    <span className="text-xs font-semibold text-slate-300 flex items-center gap-1.5">
                      <Activity className="w-4 h-4 text-cyan-400" />
                      IMU 6-Axis Sensors Real-time Data
                    </span>
                    <span className="text-[10px] text-slate-400">100Hz Hardware Polling</span>
                  </div>

                  <div className="grid grid-cols-2 gap-2 text-xs font-mono">
                    <div className="bg-slate-900 p-2 rounded border border-slate-800">
                      <span className="text-slate-500 block text-[10px]">ACCELEROMETER</span>
                      <span className="text-emerald-400 font-bold">X: 0.12 Y: 9.81 Z: 0.45</span>
                      <span className="text-[10px] text-slate-400 block mt-0.5">Magnitude: 9.82 m/s²</span>
                    </div>

                    <div className="bg-slate-900 p-2 rounded border border-slate-800">
                      <span className="text-slate-500 block text-[10px]">GYROSCOPE</span>
                      <span className="text-cyan-400 font-bold">ωX: 0.01 ωY: 0.02 ωZ: {((insHeading - 180) / 100).toFixed(2)}</span>
                      <span className="text-[10px] text-slate-400 block mt-0.5">Rad/s orientation rate</span>
                    </div>

                    <div className="bg-slate-900 p-2 rounded border border-slate-800">
                      <span className="text-slate-500 block text-[10px]">MAGNETIC FIELD</span>
                      <span className="text-amber-400 font-bold">Mx: 18.2 My: -32.4 Mz: 42.1</span>
                      <span className="text-[10px] text-slate-400 block mt-0.5">uT Geomagnetic Vector</span>
                    </div>

                    <div className="bg-slate-900 p-2 rounded border border-slate-800">
                      <span className="text-slate-500 block text-[10px]">PDR ACCUMULATED DRIFT</span>
                      <span className="text-rose-400 font-bold">N: {insDriftNorth.toFixed(2)}m E: {insDriftEast.toFixed(2)}m</span>
                      <span className="text-[10px] text-slate-400 block mt-0.5">Kalman Covariance: P=0.42</span>
                    </div>
                  </div>
                </div>
              </div>

              {/* Real-time Fused Location & DEM Terrain Sync Panel */}
              <div className="bg-slate-950 border border-slate-800 p-4 rounded-xl space-y-4">
                <div className="flex items-center justify-between border-b border-slate-800 pb-2">
                  <span className="text-xs font-semibold text-slate-300">
                    实时融合定位与 DEM 物理地形刷新 (Live Position & DEM Terrain Sync)
                  </span>
                  <span className="text-xs font-mono text-emerald-400 font-bold">
                    [来源: {
                      insNavState === 'INS_ONLY' ? 'INS' :
                      insNavState === 'HYBRID' || insNavState === 'GPS_RECOVERY' ? 'GPS+INS' : 'GPS'
                    }]
                  </span>
                </div>

                <div className="grid grid-cols-2 md:grid-cols-5 gap-3 text-center">
                  <div className="bg-slate-900 p-2.5 rounded-lg border border-slate-800">
                    <span className="text-[10px] text-slate-400 block">Latitude 纬度</span>
                    <span className="text-sm font-mono font-bold text-white">{insLat.toFixed(6)}°</span>
                  </div>

                  <div className="bg-slate-900 p-2.5 rounded-lg border border-slate-800">
                    <span className="text-[10px] text-slate-400 block">Longitude 经度</span>
                    <span className="text-sm font-mono font-bold text-white">{insLon.toFixed(6)}°</span>
                  </div>

                  <div className="bg-slate-900 p-2.5 rounded-lg border border-slate-800">
                    <span className="text-[10px] text-slate-400 block">DEM 海拔 (Elevation)</span>
                    <span className="text-sm font-mono font-bold text-amber-400">
                      {insTrackPoints[insTrackPoints.length - 1]?.elev || 754.2} m
                    </span>
                  </div>

                  <div className="bg-slate-900 p-2.5 rounded-lg border border-slate-800">
                    <span className="text-[10px] text-slate-400 block">Horn 3D 坡度 (Slope)</span>
                    <span className="text-sm font-mono font-bold text-teal-400">
                      {insTrackPoints[insTrackPoints.length - 1]?.slope || 18.5}°
                    </span>
                  </div>

                  <div className="bg-slate-900 p-2.5 rounded-lg border border-slate-800 col-span-2 md:col-span-1">
                    <span className="text-[10px] text-slate-400 block">坡向 (Aspect)</span>
                    <span className="text-sm font-mono font-bold text-cyan-400">
                      {insTrackPoints[insTrackPoints.length - 1]?.aspect || 135.0}°
                    </span>
                  </div>
                </div>

                {/* Track Point Log with Source Tagging */}
                <div className="space-y-2 pt-2">
                  <span className="text-xs font-semibold text-slate-400 block">
                    轨迹记录列表 (Track Log with Source Tagging: GPS vs INS vs GPS+INS)
                  </span>
                  
                  <div className="max-h-48 overflow-y-auto bg-slate-900 border border-slate-800 rounded-lg p-2 font-mono text-xs space-y-1">
                    {insTrackPoints.slice().reverse().map(pt => (
                      <div key={pt.id} className="flex justify-between items-center py-1 border-b border-slate-800/60 text-slate-300">
                        <div className="flex items-center gap-2">
                          <span className="text-slate-500 text-[10px]">{pt.time}</span>
                          <span className={`px-1.5 py-0.2 rounded text-[10px] font-bold ${
                            pt.source === 'INS' ? 'bg-amber-500/20 text-amber-400 border border-amber-500/30' :
                            pt.source === 'GPS+INS' ? 'bg-cyan-500/20 text-cyan-400 border border-cyan-500/30' :
                            'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30'
                          }`}>
                            [{pt.source}]
                          </span>
                          <span>Lat: {pt.lat.toFixed(6)}, Lon: {pt.lon.toFixed(6)}</span>
                        </div>
                        <div className="text-slate-400 text-[11px]">
                          Elev: <span className="text-amber-400 font-bold">{pt.elev}m</span> | Slope: {pt.slope}° | Aspect: {pt.aspect}°
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* TAB 6: Unified Audit Report and Call Trace */}
          {activeTab === 'architecture' && (
            <div className="bg-slate-900 border border-slate-800 rounded-xl p-6 shadow-md space-y-6 transition-all duration-300">
              <div className="flex items-center justify-between border-b border-slate-800 pb-4">
                <div className="flex items-center gap-2">
                  <FileText className="w-5 h-5 text-emerald-400" />
                  <h2 className="text-base font-bold text-white">统一离线底图包与 DEM 白皮书系统</h2>
                </div>
                <div className="text-emerald-400 text-xs text-right">
                  全源断电级审计通过
                </div>
              </div>

              <div className="font-mono text-xs space-y-6">
                <div>
                  <h3 className="text-amber-400 font-bold border-b border-slate-800 pb-1.5 mb-2 flex items-center gap-1.5">
                    <span className="h-1.5 w-1.5 rounded-full bg-amber-400"></span>
                    1. 离线 MBTiles 扫描与发现机制 (Map Discovery)
                  </h3>
                  <div className="bg-slate-950 border border-slate-850 p-3 rounded-lg text-slate-300 leading-relaxed text-[11px] space-y-2">
                    <p>
                      当 APK 或模拟主件启动时，会通过 IO 环境系统对外部根存储空间 <code className="text-emerald-400 bg-slate-900 px-1 rounded">/storage/emulated/0/CyberTrail/Maps/</code> 实施全面质检。
                    </p>
                    <p>
                      引擎通过 SQLite 特权驱动，读取每个 MBTiles 的 <code className="text-white px-1 rounded bg-slate-900">metadata</code> 键值表，提取出来 <code className="text-teal-400">minzoom</code>、<code className="text-teal-400">maxzoom</code>、<code className="text-teal-400">bounds</code> 两个核心元数据字段，并将其存入列表驱动程序，用在下述优先切换规则中。
                    </p>
                  </div>
                </div>

                <div>
                  <h3 className="text-amber-400 font-bold border-b border-slate-800 pb-1.5 mb-2 flex items-center gap-1.5">
                    <span className="h-1.5 w-1.5 rounded-full bg-amber-400"></span>
                    2. 高精局部包优先回退决策机制 (Tile Priority Routing)
                  </h3>
                  <div className="bg-slate-950 border border-slate-850 p-3 rounded-lg text-slate-300 leading-relaxed text-[11px] space-y-2">
                    <p>
                      当探险家向地图中心移动时，地图加载线程会循环质检已装载底图大包的覆盖经纬度盒。
                    </p>
                    <p className="border-l-2 border-emerald-500 pl-2 text-emerald-400 font-semibold bg-emerald-950/10 py-1">
                      命中判定级：Dandong.mbtiles &gt; Liaoning.mbtiles &gt; China.mbtiles &gt; World.mbtiles
                    </p>
                    <p>
                      当定位点落在 Dandong 大包范围内，且请求显示级别在 Z12-Z15 间，本地 Tile Server 会绕过全球与省级包，直接代理给 Dandong.mbtiles 读取局部精细影像瓦片，达成极限断网下的平滑流畅放大。
                    </p>
                  </div>
                </div>

                <div>
                  <h3 className="text-amber-400 font-bold border-b border-slate-800 pb-1.5 mb-2 flex items-center gap-1.5">
                    <span className="h-1.5 w-1.5 rounded-full bg-amber-400"></span>
                    3. 独立物理高程 DEM 阻断准则 (DEM Safety Code)
                  </h3>
                  <div className="bg-slate-950 border border-slate-850 p-3 rounded-lg text-slate-300 leading-relaxed text-[11px] space-y-2">
                    <p>
                      由于山野环境变幻莫测，欺骗性高程会给探险家带来巨大山难隐患。新方案彻底抛弃了对任何在线 OpenTopo API 的网络轮询。
                    </p>
                    <p>
                      如果没有本地 <code className="text-white bg-slate-900 px-1 rounded">/DEM/</code> 物理包：系统绝对禁止采取模拟函数（或傅里叶拟合等仿真模型）给出具有欺骗意味的海拔值，系统在 HUD 上坚定显示为 <code className="text-rose-400 uppercase">海拔: 无DEM数据</code> 并屏蔽坡度/坡向推演，捍卫用户安全规范。
                    </p>
                  </div>
                </div>
              </div>
            </div>
          )}
        </main>

        {/* Right Console: Diagnostic Log stream and Quick Statuses */}
        <section className="space-y-6">
          {/* Quick HUD block demonstrating live adaptation with Data Sources labels */}
          <div className="bg-slate-900 border border-slate-800 rounded-xl p-5 shadow-md space-y-4">
            <div className="flex items-center justify-between border-b border-slate-850 pb-3">
              <div className="flex items-center gap-2">
                <Activity className="w-5 h-5 text-emerald-400 animate-pulse" />
                <h2 className="text-sm font-bold text-emerald-300">终端 HUD 视觉显示窗 (HUD Telemetry)</h2>
              </div>
              <div className="flex items-center gap-1">
                <span className="h-2 w-2 rounded-full bg-emerald-400 animate-ping"></span>
                <span className="text-[9px] font-mono text-emerald-400 font-bold">LIVE TELEMETRY</span>
              </div>
            </div>

            {/* Test Data Source Selection Controls */}
            <div className="space-y-2 bg-slate-950 p-3 rounded-lg border border-slate-850">
              <span className="text-[10px] text-slate-400 block font-bold">仿真手调 HUD 数据源 (Demonstrator Trigger):</span>
              <div className="grid grid-cols-4 gap-1">
                <button 
                  id="source_btn_dem"
                  onClick={() => {
                    if (!hasDemFiles) {
                      addLog(`[UI_CONTROL] Cannot switch HUD to DEM: DEM files are currently disabled/missing.`);
                      return;
                    }
                    setHudTelemetrySource('DEM');
                    addLog(`[UI_CONTROL] Switched interactive HUD source to DEM.`);
                  }}
                  className={`py-1 rounded text-[10px] font-mono font-bold border transition ${hudTelemetrySource === 'DEM' ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30' : 'bg-slate-900 text-slate-500 border-slate-800 hover:text-slate-300'}`}
                >
                  DEM
                </button>
                <button 
                  id="source_btn_gps"
                  onClick={() => {
                    setHudTelemetrySource('GPS');
                    addLog(`[UI_CONTROL] Switched interactive HUD source to GPS Satellites.`);
                  }}
                  className={`py-1 rounded text-[10px] font-mono font-bold border transition ${hudTelemetrySource === 'GPS' ? 'bg-amber-500/10 text-amber-400 border-amber-500/30' : 'bg-slate-900 text-slate-500 border-slate-800 hover:text-slate-300'}`}
                >
                  GPS
                </button>
                <button 
                  id="source_btn_sim"
                  onClick={() => {
                    setHudTelemetrySource('SIMULATION');
                    addLog(`[UI_CONTROL] Switched interactive HUD source to SIMULATION.`);
                  }}
                  className={`py-1 rounded text-[10px] font-mono font-bold border transition ${hudTelemetrySource === 'SIMULATION' ? 'bg-rose-500/10 text-rose-400 border-rose-500/30' : 'bg-slate-900 text-slate-500 border-slate-800 hover:text-slate-300'}`}
                >
                  SIM
                </button>
                <button 
                  id="source_btn_none"
                  onClick={() => {
                    setHudTelemetrySource('NONE');
                    addLog(`[UI_CONTROL] Switched interactive HUD source to NONE (No Data).`);
                  }}
                  className={`py-1 rounded text-[10px] font-mono font-bold border transition ${hudTelemetrySource === 'NONE' ? 'bg-slate-800 text-white border-slate-700' : 'bg-slate-900 text-slate-500 border-slate-800 hover:text-slate-300'}`}
                >
                  NONE
                </button>
              </div>
            </div>

            <div className="space-y-2 font-mono">
              {/* Dynamic HUD output 1: Elevation */}
              <div className="bg-slate-950 p-3 rounded-lg border border-slate-850 flex justify-between items-center">
                <div>
                  <span className="text-slate-500 text-[10px] block leading-none mb-1">海拔高度 (Elevation)</span>
                  <span className="text-base font-bold text-white tracking-tight">
                    {hudTelemetrySource === 'DEM' && hasDemFiles ? `${simElev.toFixed(1)} m` :
                     hudTelemetrySource === 'GPS' ? '681.2 m' :
                     hudTelemetrySource === 'SIMULATION' ? '520.4 m (Not Allowed in Prod)' :
                     hudTelemetrySource === 'NONE' ? '--' :
                     '无DEM数据'}
                  </span>
                </div>
                {hudTelemetrySource !== 'NONE' && (hudTelemetrySource !== 'DEM' || hasDemFiles) ? (
                  <span className={`px-2 py-0.5 rounded text-[9px] font-bold border ${
                    hudTelemetrySource === 'DEM' ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20' :
                    hudTelemetrySource === 'GPS' ? 'bg-amber-500/10 text-amber-400 border-amber-500/20' :
                    'bg-rose-500/10 text-rose-400 border-rose-500/20'
                  }`}>
                    数据来源: {hudTelemetrySource}
                  </span>
                ) : (
                  <span className="px-2 py-0.5 rounded text-[9px] font-bold bg-rose-500/10 text-rose-400 border border-rose-500/20 uppercase">
                    无数据
                  </span>
                )}
              </div>

              {/* Dynamic HUD output 2: Slope */}
              <div className="bg-slate-950 p-3 rounded-lg border border-slate-850 flex justify-between items-center">
                <div>
                  <span className="text-slate-500 text-[10px] block leading-none mb-1">3D 测算坡度 (Slope)</span>
                  <span className="text-base font-bold text-white tracking-tight">
                    {hudTelemetrySource === 'DEM' && hasDemFiles ? `${simSlope.toFixed(1)}°` :
                     hudTelemetrySource === 'SIMULATION' ? '12.4°' :
                     '--'}
                  </span>
                </div>
                {hudTelemetrySource === 'DEM' && hasDemFiles ? (
                  <span className={`px-2 py-0.5 rounded border text-[9px] font-bold ${getSlopeBadge(simSlope).color}`}>
                    {getSlopeBadge(simSlope).text}
                  </span>
                ) : (
                  <span className="text-[9px] text-slate-500">坡角未解算</span>
                )}
              </div>

              {/* Dynamic HUD output 3: Aspect */}
              <div className="bg-slate-950 p-3 rounded-lg border border-slate-850 flex justify-between items-center">
                <div>
                  <span className="text-slate-500 text-[10px] block leading-none mb-1">坡向方位 (Aspect)</span>
                  <span className="text-base font-bold text-white tracking-tight">
                    {hudTelemetrySource === 'DEM' && hasDemFiles ? `${simAspect.toFixed(1)}°` :
                     hudTelemetrySource === 'SIMULATION' ? '198.0°' :
                     '--'}
                  </span>
                </div>
                <span className="text-[10px] text-slate-400">
                  {hudTelemetrySource === 'DEM' && hasDemFiles ? '东南偏南' : '--'}
                </span>
              </div>
            </div>
          </div>

          {/* Quick Stats Panel */}
          <div className="bg-slate-900 border border-slate-800 rounded-xl p-5 shadow-md space-y-4">
            <div className="flex items-center gap-2 border-b border-slate-850 pb-3">
              <Database className="w-5 h-5 text-emerald-400 animate-pulse" />
              <h2 className="text-sm font-bold text-emerald-300">外部存储目录质检状态 (Storage Audit)</h2>
            </div>

            <div className="space-y-3">
              <div className="bg-slate-950 p-2.5 rounded border border-slate-850 flex justify-between items-center text-xs font-mono">
                <span className="text-slate-400">/storage/.../Maps/</span>
                <span className="font-bold text-white">4 个大包 (.mbtiles)</span>
              </div>

              <div className="bg-slate-950 p-2.5 rounded border border-slate-850 flex justify-between items-center text-xs font-mono">
                <span className="text-slate-400">/storage/.../DEM/</span>
                <span className="font-bold text-teal-400">3 个地形文件 (.hgt/.bil/.tif)</span>
              </div>

              <div className="bg-slate-950 p-2.5 rounded border border-slate-850 flex justify-between items-center text-xs font-mono">
                <span className="text-slate-400">/storage/.../Downloads/</span>
                <span className="font-semibold text-amber-400">Tokyo 下载挂起 (1.2 GB)</span>
              </div>
            </div>
          </div>

          {/* Real-time system log stdout terminal */}
          <div className="bg-slate-900 border border-slate-800 rounded-xl overflow-hidden shadow-lg">
            <div className="bg-slate-950 px-4 py-3 border-b border-slate-850 flex items-center justify-between">
              <div className="flex items-center gap-1.5">
                <div className="h-2 w-2 rounded-full bg-rose-500 animate-pulse" />
                <div className="h-2 w-2 rounded-full bg-yellow-500" />
                <div className="h-2 w-2 rounded-full bg-emerald-500" />
                <span className="text-[10px] font-mono font-bold text-slate-500 ml-1.5 uppercase tracking-wider">Diagnostic Log Output</span>
              </div>
              <button 
                id="btn_clear_logs"
                onClick={() => setTerminalLogs([])}
                className="text-[10px] text-slate-500 hover:text-slate-300 transition hover:underline"
              >
                Clear
              </button>
            </div>

            <div className="p-4 bg-slate-950/40 text-left font-mono text-[10px] leading-relaxed text-slate-300 h-[280px] overflow-y-auto space-y-2">
              {terminalLogs.length === 0 ? (
                <div className="text-slate-600 text-center pt-10">Terminal absolute silent. Ready to receive events.</div>
              ) : (
                terminalLogs.map((log, index) => (
                  <div key={index} className="border-b border-slate-900/40 pb-1.5 last:border-0 hover:bg-slate-900/35 px-1 rounded transition-colors duration-150">
                    <span className="text-emerald-500/80 mr-1.5">&gt;&gt;</span> {log}
                  </div>
                ))
              )}
            </div>
          </div>
        </section>
      </div>

      {/* Primary Footer */}
      <footer className="footer bg-slate-900 border-t border-slate-800 py-6 px-6 text-xs text-slate-500 mt-auto">
        <div className="max-w-7xl mx-auto flex flex-col md:flex-row items-center justify-between gap-4">
          <div>© 2026 CyberTrail High Contrast Digital Elevation (DEM) Mapping Engine. All systems fully optimized.</div>
          <div className="flex gap-4 font-mono select-none">
            <span className="text-emerald-500 font-bold">OFFLINE_CAPABLE = TRUE</span>
            <span className="text-emerald-500 font-bold">COORDINATES_DANDONG = VALID</span>
          </div>
        </div>
      </footer>
    </div>
  );
}

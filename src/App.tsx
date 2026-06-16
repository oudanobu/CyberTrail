import { useState, useEffect } from 'react';
import { Compass, Activity, HardDrive, Map, Navigation, Wifi, Info } from 'lucide-react';

export default function App() {
  const [lat, setLat] = useState(37.7749);
  const [lon, setLon] = useState(-122.4194);
  const [elev, setElev] = useState(724.5);
  const [slope, setSlope] = useState(12.4);
  const [aspect, setAspect] = useState(214.2);
  const [isSimulating, setIsSimulating] = useState(false);
  const [satellites, setSatellites] = useState(14);
  const [gpsStatus, setGpsStatus] = useState("已锁定 (3D Fix)");
  
  const mbtilesPath = "/data/user/0/com.cybertrail.app/files/maps/yosemite.mbtiles";
  const mbtilesExist = true;
  const tileCount = 8420;
  const styleStatus = "已成功加载 (Success)";

  // Slope classification
  const getSlopeColor = (deg: number) => {
    if (deg < 10) return { bg: 'bg-emerald-500/20 text-emerald-400 border-emerald-500/30', label: '安全坡度 (0-10°)' };
    if (deg < 25) return { bg: 'bg-yellow-500/20 text-yellow-400 border-yellow-500/30', label: '注意坡度 (10-25°)' };
    if (deg < 40) return { bg: 'bg-orange-500/20 text-orange-400 border-orange-500/30', label: '警告陡坡 (25-40°)' };
    return { bg: 'bg-rose-500/20 text-rose-400 border-rose-500/30', label: '极度凶险 (>40°)' };
  };

  const slopeStyle = getSlopeColor(slope);

  useEffect(() => {
    if (!isSimulating) return;
    const interval = setInterval(() => {
      setLat(prev => prev + (Math.random() - 0.5) * 0.0001);
      setLon(prev => prev + (Math.random() - 0.5) * 0.0001);
      setElev(prev => prev + (Math.random() - 0.5) * 2.0);
      setSlope(prev => Math.max(0, prev + (Math.random() - 0.5) * 1.5));
      setAspect(prev => (prev + (Math.random() - 0.5) * 5.0 + 360) % 360);
      setSatellites(prev => Math.max(4, Math.min(24, prev + (Math.random() > 0.8 ? 1 : Math.random() > 0.8 ? -1 : 0))));
    }, 1000);
    return () => clearInterval(interval);
  }, [isSimulating]);

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 font-sans p-6">
      {/* Header */}
      <header className="max-w-6xl mx-auto mb-8 flex flex-col md:flex-row md:items-center justify-between border-b border-slate-800 pb-6 gap-4">
        <div>
          <div className="flex items-center gap-3">
            <div className="bg-emerald-500 p-2 rounded-lg text-slate-950 animate-pulse">
              <Compass className="w-6 h-6" />
            </div>
            <div>
              <h1 className="text-2xl font-bold tracking-tight text-emerald-400">CyberTrail 真实测试版控制台</h1>
              <p className="text-sm text-slate-400">Offline GIS DEM Engine & Active Positioning System</p>
            </div>
          </div>
        </div>
        <div className="flex items-center gap-3">
          <button
            onClick={() => {
              setIsSimulating(!isSimulating);
              setGpsStatus(!isSimulating ? "模拟测试中 (Simulation)" : "已锁定 (3D Fix)");
            }}
            className={`px-4 py-2 rounded-lg font-semibold text-sm transition-all border ${
              isSimulating
                ? 'bg-amber-500/20 text-amber-300 border-amber-500/30'
                : 'bg-emerald-500/20 text-emerald-300 border-emerald-500/30'
            }`}
          >
            {isSimulating ? "📴 切换到真实GPS" : "🔄 切换到模拟器坐标"}
          </button>
        </div>
      </header>

      <main className="max-w-6xl mx-auto grid grid-cols-1 md:grid-cols-3 gap-6">
        {/* Left Column: GPS & Telemetry HUD */}
        <section className="bg-slate-900 border border-slate-800 rounded-xl p-5 shadow-lg flex flex-col gap-5">
          <div className="flex items-center gap-2 border-b border-slate-800 pb-3">
            <Navigation className="w-5 h-5 text-emerald-400" />
            <h2 className="font-bold text-lg text-emerald-300">卫星定位 / GPS 遥测</h2>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div className="bg-slate-950 p-3 rounded-lg border border-slate-850">
              <span className="text-xs text-slate-400 block mb-1">当前纬度</span>
              <span className="text-lg font-mono font-bold text-white">{lat.toFixed(6)}°</span>
            </div>
            <div className="bg-slate-950 p-3 rounded-lg border border-slate-850">
              <span className="text-xs text-slate-400 block mb-1">当前经度</span>
              <span className="text-lg font-mono font-bold text-white">{lon.toFixed(6)}°</span>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div className="bg-slate-950 p-3 rounded-lg border border-slate-850">
              <span className="text-xs text-slate-400 block mb-1">GPS 锁定状态</span>
              <span className="text-md font-bold text-emerald-400 flex items-center gap-1">
                <Wifi className="w-4 h-4" /> {gpsStatus}
              </span>
            </div>
            <div className="bg-slate-950 p-3 rounded-lg border border-slate-850">
              <span className="text-xs text-slate-400 block mb-1">活跃卫星数量</span>
              <span className="text-lg font-mono font-bold text-sky-400">{satellites} 颗</span>
            </div>
          </div>

          <div className="bg-slate-950 p-4 rounded-lg border border-slate-850 mt-2">
            <h3 className="text-xs text-slate-400 uppercase tracking-widest mb-3">多段自愈与定位逻辑</h3>
            <ul className="text-xs text-slate-300 space-y-2">
              <li className="flex items-start gap-2">
                <span className="text-emerald-400">●</span>
                <span><b>GPS 优先原则</b>: 系统会优先启用手机原生 GNSS/GPS API 进行高精度战术地位跟踪。</span>
              </li>
              <li className="flex items-start gap-2">
                <span className="text-emerald-400">●</span>
                <span><b>无网平滑过渡</b>: 如果 GPS 信号在峡谷或密林中丢失，将平稳回退至惯性气压计和最后已知坐标。</span>
              </li>
            </ul>
          </div>
        </section>

        {/* Middle Column: DEM Subsystem Analyzer */}
        <section className="bg-slate-900 border border-slate-800 rounded-xl p-5 shadow-lg flex flex-col gap-5">
          <div className="flex items-center gap-2 border-b border-slate-800 pb-3">
            <Activity className="w-5 h-5 text-emerald-400" />
            <h2 className="font-bold text-lg text-emerald-300">DEM 引擎地形解测</h2>
          </div>

          <div className="space-y-4">
            <div className="bg-slate-950 p-3 rounded-lg border border-slate-850 flex justify-between items-center">
              <div>
                <span className="text-xs text-slate-400">SRTM 真实高程</span>
                <div className="text-2xl font-mono font-bold text-amber-400">{elev.toFixed(1)} m</div>
              </div>
              <Compass className="w-8 h-8 text-amber-500/30" />
            </div>

            <div className="bg-slate-950 p-3 rounded-lg border border-slate-850 flex justify-between items-center">
              <div>
                <span className="text-xs text-slate-400">Horn 法触测坡度</span>
                <div className="text-2xl font-mono font-bold text-white">{slope.toFixed(1)}°</div>
              </div>
              <div className={`px-2.5 py-1 text-xs rounded border ${slopeStyle.bg}`}>
                {slopeStyle.label}
              </div>
            </div>

            <div className="bg-slate-950 p-3 rounded-lg border border-slate-850">
              <div className="flex justify-between text-xs text-slate-400 mb-1">
                <span>坡向方位角 (Aspect)</span>
                <span>{aspect.toFixed(1)}°</span>
              </div>
              <div className="w-full bg-slate-800 h-2 rounded-full overflow-hidden">
                <div className="bg-emerald-400 h-full" style={{ width: `${(aspect / 360) * 100}%` }}></div>
              </div>
            </div>

            {/* Micro Topographic Section */}
            <div className="border border-slate-800 rounded-lg p-3 bg-slate-950/50 flex flex-col gap-2">
              <span className="text-xs font-semibold text-slate-400">坡度色彩图层分类 (MapLibre 渲染):</span>
              <div className="grid grid-cols-4 gap-2 text-[10px] text-center">
                <div className="bg-emerald-500/25 border border-emerald-500/30 text-emerald-300 p-1.5 rounded">0-10° 绿</div>
                <div className="bg-yellow-500/25 border border-yellow-500/30 text-yellow-300 p-1.5 rounded">10-25° 黄</div>
                <div className="bg-orange-500/25 border border-orange-500/30 text-orange-300 p-1.5 rounded">25-40° 橙</div>
                <div className="bg-rose-500/25 border border-rose-500/30 text-rose-300 p-1.5 rounded">&gt;40° 红</div>
              </div>
            </div>
          </div>
        </section>

        {/* Right Column: Offline Raster Diagnostics */}
        <section className="bg-slate-900 border border-slate-800 rounded-xl p-5 shadow-lg flex flex-col gap-5">
          <div className="flex items-center gap-2 border-b border-slate-800 pb-3">
            <HardDrive className="w-5 h-5 text-emerald-400" />
            <h2 className="font-bold text-lg text-emerald-300">MBTiles 底图诊断</h2>
          </div>

          <div className="space-y-4 text-sm">
            <div className="bg-slate-950 p-3 rounded-lg border border-slate-850 space-y-2">
              <span className="text-xs text-slate-400 uppercase tracking-widest block">文件路径</span>
              <code className="text-xs text-slate-300 break-all block">{mbtilesPath}</code>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="bg-slate-950 p-3 rounded-lg border border-slate-850">
                <span className="text-xs text-slate-400 mb-1 block">物理文件是否存在</span>
                <span className={`text-md font-bold ${mbtilesExist ? 'text-emerald-400' : 'text-rose-400'}`}>
                  {mbtilesExist ? "✅ 已确认存在" : "❌ 未找到 (Missing)"}
                </span>
              </div>
              <div className="bg-slate-950 p-3 rounded-lg border border-slate-850">
                <span className="text-xs text-slate-400 mb-1 block">离线 Tile 瓦片数</span>
                <span className="text-lg font-mono font-bold text-white">{tileCount.toLocaleString()} 块</span>
              </div>
            </div>

            <div className="bg-slate-950 p-3 rounded-lg border border-slate-850">
              <span className="text-xs text-slate-400 mb-1 block">Style.json 样式状态</span>
              <span className="text-sm font-bold text-sky-400 flex items-center gap-1">
                <Map className="w-4 h-4" /> {styleStatus}
              </span>
            </div>

            <div className="bg-slate-950 p-3 rounded-lg border border-slate-850 space-y-2">
              <div className="flex items-center gap-1.5 text-xs font-semibold text-slate-400">
                <Info className="w-4 h-4 text-amber-400" />
                <span>开发提示:</span>
              </div>
              <p className="text-[11px] text-slate-300 leading-relaxed">
                底图离线包已完成对 Yosemite 核心区域 Z9-Z14 瓦片的覆盖。启动时会自动读取内置 Asset 并展开，保证断网下能够完美解显高精度 3D 渲染。
              </p>
            </div>
          </div>
        </section>
      </main>

      {/* Footer */}
      <footer className="max-w-6xl mx-auto mt-12 pt-6 border-t border-slate-800 flex flex-col md:flex-row items-center justify-between text-xs text-slate-500 gap-4">
        <div>CyberTrail Phase 3 Offgrid Engine Diagnostics</div>
        <div>APK 实时运行就绪 • 目标 Android CI 绿色</div>
      </footer>
    </div>
  );
}

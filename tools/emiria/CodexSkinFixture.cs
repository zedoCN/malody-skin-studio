#if UNITY_EDITOR
using System;
using System.Collections.Generic;
using System.IO;
using System.Security.Cryptography;
using Malody.Chart;
using Malody.Composer;
using Malody.Manager;
using Malody.Play;
using Malody.Scene;
using Malody.Scene.Play.Skin;
using Malody.Scene.Play.Skin.Module;
using Malody.Skin;
using UnityEditor;
using UnityEngine;
using UnityEngine.SceneManagement;
using UnityEngine.UI;

namespace Sentnet.Toolchain.UnityAutomation.Editor
{
    /// <summary>
    /// Local, disposable Malody V visual fixture. Configuration lives under Library, outside Assets.
    /// </summary>
    [InitializeOnLoad]
    internal static class CodexSkinFixture
    {
        private const string MenuPath = "Tools/Malody/Codex/Play Skin Fixture";
        private const string PendingKey = "CodexSkinFixture.Stage";
        private const string StartedKey = "CodexSkinFixture.StartedUtc";
        private const string TargetKey = "CodexSkinFixture.FreezeAtAudioMs";
        private const string Waiting = "waiting-for-song";
        private const string Entering = "entering-scene-play";
        private const string WaitingForFrame = "waiting-for-frame";
        private const string Pausing = "pausing";
        private static double songReadySince = -1;
        private static ChartPlayer watchedPlayer;
        private static int pauseFrame;

        [Serializable]
        private sealed class FixtureConfig
        {
            public string skinDir;
            public string chartPath;
            public int startOffset;
            public int freezeAtAudioMs;
        }

        [Serializable]
        private sealed class FixtureStatus
        {
            public string phase;
            public string detail;
            public string utc;
            public bool hasTime;
            public double audioTimeMs;
            public double chartTimeMs;
            public double displayTimeMs;
            public int unityFrame;
        }

        [Serializable]
        private sealed class ModuleExport
        {
            public int schemaVersion = 1;
            public string utc;
            public int unityReportedScreenWidth;
            public int unityReportedScreenHeight;
            public double audioTimeMs;
            public double chartTimeMs;
            public double displayTimeMs;
            public string skinTitle;
            public string skinScriptFile;
            public string asmSha256;
            public string luaHash;
            public string chartFile;
            public int factoryCount;
            public int moduleCount;
            public string[] duplicateNames;
            public ModuleSnapshot[] modules;
        }

        [Serializable]
        private sealed class ModuleSnapshot
        {
            public string name;
            public string unityObjectName;
            public int factoryLayer;
            public string factoryName;
            public SourceSnapshot source;
            public RuntimeSnapshot runtime;
            public string error;
        }

        [Serializable]
        private sealed class SourceSnapshot
        {
            public int layer;
            public int order;
            public int type;
            public int usage;
            public float x;
            public float y;
            public float dx;
            public float dy;
            public string xUnit;
            public string yUnit;
            public string dxUnit;
            public string dyUnit;
            public int alpha;
            public bool hasImage;
            public string imageFile;
            public float imageWidth;
            public float imageHeight;
            public string imageWidthUnit;
            public string imageHeightUnit;
        }

        [Serializable]
        private sealed class RuntimeSnapshot
        {
            public float x;
            public float y;
            public float width;
            public float height;
            public int alpha;
            public float scale;
            public float rotate;
            public bool hasRectTransform;
            public Vector2 rectSize;
            public Vector2 anchoredPosition;
            public Vector2 pivot;
            public Vector3 localScale;
            public bool hasParentCanvas;
            public Vector2 parentCanvasSize;
            public bool hasCanvasReferenceResolution;
            public Vector2 canvasReferenceResolution;
        }

        private sealed class CaptureException : Exception
        {
            public CaptureException(string reason) : base(reason) { }
        }

        private static string ProjectRoot => Path.GetDirectoryName(Application.dataPath);
        private static string ConfigPath => Path.Combine(ProjectRoot, "Library/CodexSkinFixture.json");
        private static string StatusPath => Path.Combine(ProjectRoot, "Library/CodexSkinFixture.status.json");
        private static string ModulesPath => Path.Combine(ProjectRoot, "Library/CodexSkinFixture.modules.json");

        static CodexSkinFixture()
        {
            EditorApplication.update += Update;
        }

        [MenuItem(MenuPath)]
        private static void Play()
        {
            if (SessionState.GetString(PendingKey, "").Length > 0)
            {
                Debug.LogWarning("[CodexSkinFixture] A fixture launch is already in progress.");
                return;
            }

            if (!TryReadConfig(out var config, out var error))
            {
                Fail(error);
                return;
            }

            SessionState.SetString(StartedKey, DateTime.UtcNow.ToString("O"));
            SessionState.SetInt(TargetKey, config.freezeAtAudioMs);
            SessionState.SetString(PendingKey, Waiting);
            SetStatus(Waiting, "Waiting for PlayMode, SceneSong and the chart scan to finish.");
            if (!EditorApplication.isPlayingOrWillChangePlaymode)
                EditorApplication.isPlaying = true;
        }

        [MenuItem("Tools/Malody/Codex/Hide Skin Layer 1")]
        private static void HideBackground() => SetLayerVisible(SkinLayer.Background, false);

        [MenuItem("Tools/Malody/Codex/Show Skin Layer 1")]
        private static void ShowBackground() => SetLayerVisible(SkinLayer.Background, true);

        [MenuItem("Tools/Malody/Codex/Hide Skin Layer 4")]
        private static void HideAbove() => SetLayerVisible(SkinLayer.Above, false);

        [MenuItem("Tools/Malody/Codex/Show Skin Layer 4")]
        private static void ShowAbove() => SetLayerVisible(SkinLayer.Above, true);

        [MenuItem("Tools/Malody/Codex/Export Module State")]
        private static void ExportModuleState()
        {
            if (!IsChartTimeFrozen())
            {
                Debug.LogError("[CodexSkinFixture] Export requires a chart-time-frozen ScenePlay fixture.");
                return;
            }

            var skin = SkinRuntime.instance;
            var chart = PlayInfo.InUse?.Chart;
            if (!skin || !skin.Valid || skin.Meta == null || chart == null)
            {
                Debug.LogError("[CodexSkinFixture] No valid skin runtime or chart is active.");
                return;
            }

            try
            {
                var rows = new List<ModuleSnapshot>();
                var names = new Dictionary<string, int>(StringComparer.Ordinal);
                var factories = UnityEngine.Object.FindObjectsByType<SkinRuntimeFactory>(
                    FindObjectsInactive.Include, FindObjectsSortMode.None);
                var factoryCount = 0;
                foreach (var factory in factories)
                {
                    if (factory.targetLayer != SkinLayer.Background && factory.targetLayer != SkinLayer.Above)
                        continue;
                    factoryCount++;
                    foreach (var module in factory.GetComponentsInChildren<SkinRuntimeModule>(true))
                    {
                        // Nested factories own their own modules.
                        if (module.GetComponentInParent<SkinRuntimeFactory>(true) != factory)
                            continue;
                        var row = new ModuleSnapshot
                        {
                            unityObjectName = module.name,
                            factoryLayer = (int)factory.targetLayer,
                            factoryName = factory.name,
                        };
                        rows.Add(row);
                        try
                        {
                            var source = module.Module;
                            if (source == null || source.Param == null)
                                throw new CaptureException("Missing source module or Param");
                            row.name = source.Meta?.Desc ?? "";
                            if (!string.IsNullOrEmpty(row.name))
                            {
                                names.TryGetValue(row.name, out var seen);
                                names[row.name] = seen + 1;
                            }
                            row.source = CaptureSource(source);
                            row.runtime = CaptureRuntime(module);
                        }
                        catch (Exception exception)
                        {
                            // Exception messages can contain machine-local paths; export only the type and a safe reason.
                            row.error = exception is CaptureException
                                ? exception.Message
                                : exception.GetType().Name;
                        }
                    }
                }

                var duplicates = new List<string>();
                foreach (var pair in names)
                    if (pair.Value > 1) duplicates.Add(pair.Key);
                duplicates.Sort(StringComparer.Ordinal);
                var output = new ModuleExport
                {
                    utc = DateTime.UtcNow.ToString("O"),
                    unityReportedScreenWidth = Screen.width,
                    unityReportedScreenHeight = Screen.height,
                    audioTimeMs = Finite(PlayTime.AudioTime, "audioTimeMs"),
                    chartTimeMs = Finite(PlayTime.ChartTime, "chartTimeMs"),
                    displayTimeMs = Finite(PlayTime.DisplayTime, "displayTimeMs"),
                    skinTitle = skin.Meta.Title,
                    skinScriptFile = FileNameOnly(skin.Meta.Script),
                    asmSha256 = Sha256(Path.Combine(skin.BaseDir, "info.asm")),
                    luaHash = skin.ValidSkin.LuaHash(),
                    chartFile = FileNameOnly(chart.FilePath),
                    factoryCount = factoryCount,
                    moduleCount = rows.Count,
                    duplicateNames = duplicates.ToArray(),
                    modules = rows.ToArray(),
                };
                File.WriteAllText(ModulesPath, JsonUtility.ToJson(output, true));
                Debug.Log("[CodexSkinFixture] Exported " + rows.Count + " modules from " + factoryCount +
                          " factories to " + ModulesPath);
            }
            catch (Exception exception)
            {
                Debug.LogError("[CodexSkinFixture] Module export failed: " + exception.GetType().Name);
            }
        }

        private static bool IsChartTimeFrozen()
        {
            if (!EditorApplication.isPlaying || SceneManager.GetActiveScene().name != "ScenePlay" ||
                PlayTime.IsPlaying || SessionState.GetString(PendingKey, "").Length != 0 ||
                !File.Exists(StatusPath))
                return false;
            try
            {
                var status = JsonUtility.FromJson<FixtureStatus>(File.ReadAllText(StatusPath));
                return status != null && status.phase == "chart-time-frozen" && status.hasTime &&
                    Math.Abs(status.audioTimeMs - PlayTime.AudioTime) < 0.01 &&
                    Math.Abs(status.chartTimeMs - PlayTime.ChartTime) < 0.01;
            }
            catch (Exception)
            {
                return false;
            }
        }

        private static SourceSnapshot CaptureSource(Malody.DB.SkinFile.Types.Module module)
        {
            var param = module.Param;
            var image = module.Image;
            return new SourceSnapshot
            {
                layer = param.Layer,
                order = param.Order,
                type = module.Type,
                usage = module.Usage,
                x = Finite(param.X, "source.x"),
                y = Finite(param.Y, "source.y"),
                dx = Finite(param.Dx, "source.dx"),
                dy = Finite(param.Dy, "source.dy"),
                xUnit = param.Xu.ToString(),
                yUnit = param.Yu.ToString(),
                dxUnit = param.Dxu.ToString(),
                dyUnit = param.Dyu.ToString(),
                alpha = param.Alpha,
                hasImage = image != null,
                imageFile = image == null ? null : FileNameOnly(image.File),
                imageWidth = image == null ? 0 : Finite(image.Width, "source.imageWidth"),
                imageHeight = image == null ? 0 : Finite(image.Height, "source.imageHeight"),
                imageWidthUnit = image?.Wu.ToString(),
                imageHeightUnit = image?.Hu.ToString(),
            };
        }

        private static RuntimeSnapshot CaptureRuntime(SkinRuntimeModule module)
        {
            var result = new RuntimeSnapshot
            {
                x = Finite(module.X, "runtime.x"),
                y = Finite(module.Y, "runtime.y"),
                width = Finite(module.Width, "runtime.width"),
                height = Finite(module.Height, "runtime.height"),
                alpha = module.Alpha,
                scale = Finite(module.Scale, "runtime.scale"),
                rotate = Finite(module.Rotate, "runtime.rotate"),
            };
            var rect = module.GetComponent<RectTransform>();
            if (rect)
            {
                result.hasRectTransform = true;
                result.rectSize = Finite(rect.rect.size, "rectSize");
                result.anchoredPosition = Finite(rect.anchoredPosition, "anchoredPosition");
                result.pivot = Finite(rect.pivot, "pivot");
                result.localScale = Finite(rect.localScale, "localScale");
            }
            var canvas = module.GetComponentInParent<Canvas>(true);
            if (canvas)
            {
                var canvasRect = canvas.GetComponent<RectTransform>();
                if (canvasRect)
                {
                    result.hasParentCanvas = true;
                    result.parentCanvasSize = Finite(canvasRect.rect.size, "parentCanvasSize");
                }
                var scaler = canvas.GetComponent<CanvasScaler>();
                if (scaler)
                {
                    result.hasCanvasReferenceResolution = true;
                    result.canvasReferenceResolution = Finite(scaler.referenceResolution, "canvasReferenceResolution");
                }
            }
            return result;
        }

        private static float Finite(float value, string field)
        {
            if (float.IsNaN(value) || float.IsInfinity(value))
                throw new CaptureException("Non-finite " + field);
            return value;
        }

        private static double Finite(double value, string field)
        {
            if (double.IsNaN(value) || double.IsInfinity(value))
                throw new CaptureException("Non-finite " + field);
            return value;
        }

        private static Vector2 Finite(Vector2 value, string field)
        {
            return new Vector2(Finite(value.x, field + ".x"), Finite(value.y, field + ".y"));
        }

        private static Vector3 Finite(Vector3 value, string field)
        {
            return new Vector3(Finite(value.x, field + ".x"), Finite(value.y, field + ".y"),
                Finite(value.z, field + ".z"));
        }

        private static string FileNameOnly(string path)
        {
            if (string.IsNullOrEmpty(path)) return path;
            var lastSeparator = Math.Max(path.LastIndexOf('/'), path.LastIndexOf('\\'));
            return path.Substring(lastSeparator + 1);
        }

        private static string Sha256(string path)
        {
            using (var sha = SHA256.Create())
            using (var stream = File.OpenRead(path))
                return BitConverter.ToString(sha.ComputeHash(stream)).Replace("-", "").ToLowerInvariant();
        }

        private static void SetLayerVisible(SkinLayer layer, bool visible)
        {
            if (!EditorApplication.isPlaying || SceneManager.GetActiveScene().name != "ScenePlay")
            {
                Debug.LogError("[CodexSkinFixture] Layer visibility requires ScenePlay in PlayMode.");
                return;
            }

            var count = 0;
            foreach (var factory in UnityEngine.Object.FindObjectsByType<SkinRuntimeFactory>(
                         FindObjectsInactive.Include, FindObjectsSortMode.None))
            {
                if (factory.targetLayer != layer) continue;
                var canvas = factory.GetComponent<Canvas>();
                if (!canvas)
                {
                    Debug.LogError("[CodexSkinFixture] Missing Canvas on " + factory.name);
                    continue;
                }
                canvas.enabled = visible;
                count++;
            }
            if (count == 0)
                Debug.LogError("[CodexSkinFixture] No Canvas found for skin layer " + (int)layer + ".");
            else
                Debug.Log("[CodexSkinFixture] Skin layer " + (int)layer + " visible=" + visible +
                          "; canvases=" + count);
        }

        private static void Update()
        {
            var stage = SessionState.GetString(PendingKey, "");
            if (stage.Length == 0)
                return;

            var timeoutSeconds = Math.Max(60, SessionState.GetInt(TargetKey, 0) / 1000d + 30);
            if (!DateTime.TryParse(SessionState.GetString(StartedKey, ""), out var started) ||
                DateTime.UtcNow - started.ToUniversalTime() > TimeSpan.FromSeconds(timeoutSeconds))
            {
                Fail("Timed out while waiting for " + stage + ".");
                return;
            }

            if (!EditorApplication.isPlaying)
                return;

            if (stage == Entering)
            {
                if (SceneManager.GetActiveScene().name == "ScenePlay" &&
                    UnityEngine.Object.FindFirstObjectByType<ScenePlay>() != null)
                {
                    if (SessionState.GetInt(TargetKey, 0) > 0)
                    {
                        SessionState.SetString(PendingKey, WaitingForFrame);
                        SetStatus(WaitingForFrame, "ScenePlay is active; waiting for audio time " +
                            SessionState.GetInt(TargetKey, 0) + " ms.");
                    }
                    else
                    {
                        SetStatus("scene-play", "ScenePlay is active; Game View can be captured after loading finishes.");
                        ClearPending();
                    }
                }
                return;
            }

            if (stage == WaitingForFrame)
            {
                var player = UnityEngine.Object.FindFirstObjectByType<ChartPlayer>();
                if (player && player.LoadedChart != null && watchedPlayer != player)
                {
                    UnwatchPlayer();
                    watchedPlayer = player;
                    watchedPlayer.onUpdate.AddListener(OnPlayerFrame);
                }
                return;
            }

            if (stage == Pausing)
            {
                // DoStreamPause also awaits PlayBehavior.OnPause, including the Lua runtime.
                if (!PlayTime.IsPlaying && Time.frameCount >= pauseFrame + 2)
                {
                    SetStatus("chart-time-frozen", "ChartPlayer and PlayBehavior are paused.", true);
                    ClearPending();
                }
                return;
            }

            if (SceneManager.GetActiveScene().name != "SceneSong" ||
                UnityEngine.Object.FindFirstObjectByType<SceneSong>() == null)
            {
                songReadySince = -1;
                return;
            }

            if (songReadySince < 0)
                songReadySince = EditorApplication.timeSinceStartup;
            if (EditorApplication.timeSinceStartup - songReadySince < 0.25)
                return;

            try
            {
                var manager = ChartManager.G;
                if (manager.Scanning || SkinManager.G.Scanning)
                    return;

                if (!TryReadConfig(out var config, out var error))
                {
                    Fail(error);
                    return;
                }

                var chartDir = Path.GetDirectoryName(config.chartPath);
                MaChart chart = null;
                for (var i = 0; i < manager.RawCount; i++)
                {
                    var song = manager.GetSongAt(i);
                    if (song == null || !SamePath(song.Path, chartDir))
                        continue;

                    song.ScanFiles();
                    foreach (var candidate in song.ShowList)
                    {
                        if (SamePath(candidate.FilePath, config.chartPath))
                        {
                            chart = candidate;
                            break;
                        }
                    }
                    break;
                }

                if (chart == null)
                    return; // The startup scan may still be publishing its song list.

                var skin = new Skin();
                if (!skin.Setup(config.skinDir, false))
                {
                    Fail("Skin.Setup failed for " + config.skinDir);
                    return;
                }
                if (skin.SupportPlayMode(chart.PlayMode, (int)chart.meta.Column) == Skin.PlayModeSupport.NotSupported)
                {
                    Fail("Skin does not support chart mode " + chart.PlayMode + ".");
                    return;
                }

                var info = PlayInfo.Local.Dump();
                info.ResetForNormal();
                info.Chart = chart;
                info.playFrom = PlayFrom.Composer;
                info.playBy = PlayByType.Player;
                info.startOffset = config.startOffset;
                if (!info.Validate())
                {
                    Fail("PlayInfo.Validate failed for " + config.chartPath);
                    return;
                }

                PlayInfo.InUse = info;
                var intent = new Intent.IntentPlay
                {
                    assignSkin = skin,
                    throwSkinError = true,
                    onQuit = () => SceneLoader.ToScene(SceneLoader.Type.Song),
                    onFinish = () => SceneLoader.ToScene(SceneLoader.Type.Song),
                };
                Intent.Value = intent;
                SessionState.SetString(PendingKey, Entering);
                SetStatus(Entering, "Chart=" + chart.FilePath + "; skin=" + skin.BaseDir +
                    "; startOffset=" + config.startOffset + " ms.");
                SceneLoader.ToScene(SceneLoader.Type.Play);
            }
            catch (Exception exception)
            {
                Fail(exception.ToString());
            }
        }

        private static void OnPlayerFrame()
        {
            if (PlayTime.AudioTime < SessionState.GetInt(TargetKey, 0)) return;
            var scene = UnityEngine.Object.FindFirstObjectByType<ScenePlay>();
            if (!scene)
            {
                Fail("ScenePlay disappeared before the target frame.");
                return;
            }
            UnwatchPlayer();
            pauseFrame = Time.frameCount;
            SessionState.SetString(PendingKey, Pausing);
            scene.DoStreamPause();
            SetStatus(Pausing, "Target audio time reached; pausing chart and behaviors.", true);
        }

        private static void UnwatchPlayer()
        {
            if (watchedPlayer) watchedPlayer.onUpdate.RemoveListener(OnPlayerFrame);
            watchedPlayer = null;
        }

        private static void ClearPending()
        {
            UnwatchPlayer();
            SessionState.EraseString(PendingKey);
            SessionState.EraseString(StartedKey);
            SessionState.EraseInt(TargetKey);
        }

        private static bool TryReadConfig(out FixtureConfig config, out string error)
        {
            config = null;
            error = null;
            try
            {
                if (!File.Exists(ConfigPath))
                {
                    error = "Missing " + ConfigPath +
                        ". Expected JSON: {\"skinDir\":\"/absolute/skin\",\"chartPath\":\"/absolute/slide_easy.mc\",\"startOffset\":0}";
                    return false;
                }

                config = JsonUtility.FromJson<FixtureConfig>(File.ReadAllText(ConfigPath));
                if (config == null || string.IsNullOrWhiteSpace(config.skinDir) ||
                    string.IsNullOrWhiteSpace(config.chartPath) ||
                    !Path.IsPathRooted(config.skinDir) || !Path.IsPathRooted(config.chartPath))
                {
                    error = "skinDir and chartPath must be absolute paths in " + ConfigPath;
                    return false;
                }

                config.skinDir = Path.GetFullPath(config.skinDir);
                config.chartPath = Path.GetFullPath(config.chartPath);
                if (!Directory.Exists(config.skinDir) ||
                    !File.Exists(Path.Combine(config.skinDir, "info.asm")) ||
                    !File.Exists(config.chartPath) ||
                    !string.Equals(Path.GetExtension(config.chartPath), ".mc", StringComparison.OrdinalIgnoreCase) ||
                    config.startOffset < 0 || config.freezeAtAudioMs < 0)
                {
                    error = "Fixture paths must point to an extracted skin with info.asm and an existing .mc chart; time values must be nonnegative milliseconds.";
                    return false;
                }
                return true;
            }
            catch (Exception exception)
            {
                error = "Cannot read fixture configuration: " + exception.Message;
                return false;
            }
        }

        private static bool SamePath(string left, string right)
        {
            return string.Equals(Path.GetFullPath(left).TrimEnd(Path.DirectorySeparatorChar),
                Path.GetFullPath(right).TrimEnd(Path.DirectorySeparatorChar), StringComparison.Ordinal);
        }

        private static void Fail(string detail)
        {
            ClearPending();
            songReadySince = -1;
            SetStatus("failed", detail);
            Debug.LogError("[CodexSkinFixture] " + detail);
        }

        private static void SetStatus(string phase, string detail, bool includeTime = false)
        {
            File.WriteAllText(StatusPath, JsonUtility.ToJson(new FixtureStatus
            {
                phase = phase,
                detail = detail,
                utc = DateTime.UtcNow.ToString("O"),
                hasTime = includeTime,
                audioTimeMs = includeTime ? PlayTime.AudioTime : 0,
                chartTimeMs = includeTime ? PlayTime.ChartTime : 0,
                displayTimeMs = includeTime ? PlayTime.DisplayTime : 0,
                unityFrame = includeTime ? Time.frameCount : 0,
            }, true));
            Debug.Log("[CodexSkinFixture] " + phase + ": " + detail);
        }
    }
}
#endif

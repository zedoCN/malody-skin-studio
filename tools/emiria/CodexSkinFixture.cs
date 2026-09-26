#if UNITY_EDITOR
using System;
using System.IO;
using Malody.Chart;
using Malody.Composer;
using Malody.Manager;
using Malody.Play;
using Malody.Scene;
using Malody.Scene.Play.Skin;
using Malody.Skin;
using UnityEditor;
using UnityEngine;
using UnityEngine.SceneManagement;

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

        private static string ProjectRoot => Path.GetDirectoryName(Application.dataPath);
        private static string ConfigPath => Path.Combine(ProjectRoot, "Library/CodexSkinFixture.json");
        private static string StatusPath => Path.Combine(ProjectRoot, "Library/CodexSkinFixture.status.json");

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

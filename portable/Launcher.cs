// SPDX-License-Identifier: AGPL-3.0-only
// Windows' built-in .NET Framework runs this bootstrap; the Java runtime is embedded.
using System;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Reflection;
using System.Text;
using System.Windows.Forms;

internal static class Launcher {
    [STAThread]
    private static int Main(string[] args) {
        string root = Path.GetDirectoryName(Assembly.GetExecutingAssembly().Location);
        try {
            Directory.SetCurrentDirectory(root);
            foreach (string dir in new[] { "app", "data", "cache", "cache/tmp", "logs" })
                Directory.CreateDirectory(Path.Combine(root, dir));
            FileStream instance;
            try { instance = new FileStream(Path.Combine(root, "data", "running.lock"), FileMode.OpenOrCreate, FileAccess.ReadWrite, FileShare.None); }
            catch (IOException) { MessageBox.Show("APRSIS64 is already running from this folder.", "APRSIS64"); return 1; }
            using (instance) {
                string engine = Path.Combine(root, "app", PayloadInfo.Id);
                string marker = Path.Combine(engine, ".complete");
                if (!File.Exists(marker)) {
                    Directory.CreateDirectory(engine);
                    string prefix = Path.GetFullPath(engine) + Path.DirectorySeparatorChar;
                    using (Stream payload = Assembly.GetExecutingAssembly().GetManifestResourceStream("payload.zip"))
                    using (var zip = new ZipArchive(payload, ZipArchiveMode.Read)) {
                        foreach (ZipArchiveEntry entry in zip.Entries) {
                            string target = Path.GetFullPath(Path.Combine(engine, entry.FullName));
                            if (!target.StartsWith(prefix, StringComparison.OrdinalIgnoreCase)) throw new InvalidDataException("Invalid portable payload path.");
                            if (entry.Name.Length == 0) { Directory.CreateDirectory(target); continue; }
                            Directory.CreateDirectory(Path.GetDirectoryName(target));
                            using (Stream input = entry.Open())
                            using (var output = new FileStream(target, FileMode.Create, FileAccess.Write, FileShare.None)) input.CopyTo(output);
                        }
                    }
                    File.WriteAllText(marker, PayloadInfo.Id);
                }
                string logPath = Path.Combine(root, "logs", "APRSIS64.log");
                if (File.Exists(logPath)) File.Copy(logPath, logPath + ".previous", true);
                using (var log = new StreamWriter(logPath, false, Encoding.UTF8)) {
                    log.AutoFlush = true;
                    var info = new ProcessStartInfo(Path.Combine(engine, "APRSIS64.exe"));
                    info.WorkingDirectory = root;
                    info.Arguments = string.Join(" ", Array.ConvertAll(args, Quote));
                    info.UseShellExecute = false;
                    info.CreateNoWindow = true;
                    info.RedirectStandardOutput = true;
                    info.RedirectStandardError = true;
                    info.EnvironmentVariables["APRSIS64_HOME"] = root;
                    string temp = Path.Combine(root, "cache", "tmp");
                    info.EnvironmentVariables["TEMP"] = temp;
                    info.EnvironmentVariables["TMP"] = temp;
                    info.EnvironmentVariables["JAVA_TOOL_OPTIONS"] =
                        "-Djava.io.tmpdir=" + Quote(temp) + " -Duser.home=" + Quote(Path.Combine(root, "data"));
                    using (var process = new Process { StartInfo = info }) {
                        process.OutputDataReceived += (sender, e) => { if (e.Data != null) lock(log) log.WriteLine(e.Data); };
                        process.ErrorDataReceived += (sender, e) => { if (e.Data != null) lock(log) log.WriteLine(e.Data); };
                        process.Start(); process.BeginOutputReadLine(); process.BeginErrorReadLine(); process.WaitForExit();
                        if (process.ExitCode != 0) MessageBox.Show("APRSIS64 could not start. See logs\\APRSIS64.log in this folder.", "APRSIS64");
                        return process.ExitCode;
                    }
                }
            }
        } catch (Exception ex) {
            MessageBox.Show("Unable to run APRSIS64 in this folder. Choose a writable folder and try again.\n\n" + ex.Message, "APRSIS64");
            return 1;
        }
    }

    // Windows command-line quoting, including trailing slashes and embedded quotes.
    private static string Quote(string value) {
        var result = new StringBuilder("\"");
        int slashes = 0;
        foreach (char c in value) {
            if (c == '\\') { slashes++; continue; }
            if (c == '"') result.Append('\\', slashes * 2 + 1).Append(c);
            else result.Append('\\', slashes).Append(c);
            slashes = 0;
        }
        return result.Append('\\', slashes * 2).Append('"').ToString();
    }
}

package com.tugalsan.lib.file.pdf.from.html.server;

import com.tugalsan.api.file.properties.server.TS_FilePropertiesUtils;
import com.tugalsan.api.file.server.TS_FileUtils;
import com.tugalsan.api.log.server.TS_Log;
import com.tugalsan.api.os.server.TS_OsJavaUtils;
import com.tugalsan.api.os.server.TS_OsProcess;
import com.tugalsan.api.union.client.TGS_UnionExcuse;
import com.tugalsan.api.unsafe.client.TGS_UnSafe;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

@Deprecated //JUST USE com.tugalsan.api.file.pdf.pdfbox3.serverTS_FilePdfBox3UtilsHtml.toHtml
public class TS_LibFilePdfFromHtmlUtils {

    final private static TS_Log d = TS_Log.of(true, TS_LibFilePdfFromHtmlUtils.class);

    public static Path pathDriver() {
        var driverPackageName = TS_LibFilePdfFromHtmlUtils.class.getPackageName().replace(".server", "").replace(".lib.", ".dsk.");
        return List.of(File.listRoots()).stream()
                .map(p -> Path.of(p.toString()))
                .map(p -> p.resolve("bin"))
                .map(p -> p.resolve(driverPackageName))
                .map(p -> p.resolve("home"))
                .map(p -> p.resolve("target"))
                .map(p -> p.resolve(driverPackageName + "-1.0-SNAPSHOT-jar-with-dependencies.jar"))
                .filter(p -> TS_FileUtils.isExistFile(p))
                .findAny().orElse(null);
    }

    public static Path pathOutput(Path rawPdf) {
        var label = TS_FileUtils.getNameLabel(rawPdf);
        return rawPdf.resolveSibling(label + ".html");
    }

    public static Path pathConfig(Path file) {
        return file.resolveSibling("config.properties");
    }

    public static Properties makeConfig(Path pathInput) {
        var props = new Properties();
        props.setProperty(CONFIG_PARAM_PATH_INPUT, pathInput.toAbsolutePath().toString());
        return props;
    }
    public static String CONFIG_PARAM_PATH_INPUT = "pathInput";
    public static String EXECUTE_PARAM_LOAD_CONFIG_FILE = "--load-properties-file";

    public static TGS_UnionExcuse<Path> execute(Path driver, Path pathInput) {
        return TGS_UnSafe.call(() -> {
            d.ci("execute", "pathInput", pathInput);
            //CREATE TMP-INPUT BY MAIN-INPUT
            var tmp = Files.createTempDirectory("tmp").toAbsolutePath();
            var _pathInput = tmp.resolve("_pathInput.pdf");
            TS_FileUtils.copyAs(pathInput, _pathInput, true);

            //IF DONE, COPY TMP-OUTPUT TO MAIN-OUTPUT
            var u = _execute(driver, _pathInput);
            if (u.isExcuse()) {
                return u.toExcuse();
            }
            var pdfOutput = pathOutput(pathInput);
            TS_FileUtils.copyAs(u.value(), pdfOutput, true);

            return TGS_UnionExcuse.of(pdfOutput);
        }, e -> TGS_UnionExcuse.ofExcuse(e));
    }

    private static TGS_UnionExcuse<Path> _execute(Path driver, Path pathInput) {
        var pathOutput = pathOutput(pathInput);
        d.ci("_execute", "pathOutput", pathOutput);
        var pathConfig = pathConfig(pathInput);
        d.ci("_execute", "pathConfig", pathConfig);
        TS_FilePropertiesUtils.write(makeConfig(pathInput), pathConfig);
        return TGS_UnSafe.call(() -> {
            d.ci("_execute", "rawPdf", pathInput);
            //CHECK IN-FILE
            if (pathInput == null || !TS_FileUtils.isExistFile(pathInput)) {
                return TGS_UnionExcuse.ofExcuse(d.className, "_execute", "pathInput not exists-" + pathInput);
            }
            if (TS_FileUtils.isEmptyFile(pathInput)) {
                return TGS_UnionExcuse.ofExcuse(d.className, "_execute", "pathInput is empty-" + pathInput);
            }
            //CHECK OUT-FILE
            TS_FileUtils.deleteFileIfExists(pathOutput);
            if (TS_FileUtils.isExistFile(pathOutput)) {
                return TGS_UnionExcuse.ofExcuse(d.className, "_execute", "pathOutput cleanup error-" + pathOutput);
            }
            //EXECUTE
            List<String> args = new ArrayList();
            args.add("\"" + TS_OsJavaUtils.getPathJava().resolveSibling("java.exe") + "\"");
            args.add("-jar");
            args.add("\"" + driver.toAbsolutePath().toString() + "\"");
            args.add(EXECUTE_PARAM_LOAD_CONFIG_FILE);
            args.add("\"" + pathConfig.toAbsolutePath().toString() + "\"");
            d.cr("_execute", "args", args);
            var cmd = args.stream().collect(Collectors.joining(" "));
            d.cr("_execute", "cmd", cmd);
            var p = TS_OsProcess.of(args);
            //CHECK OUT-FILE
            if (!TS_FileUtils.isExistFile(pathOutput)) {
                d.ce("_execute", "cmd", p.toString());
                return TGS_UnionExcuse.ofExcuse(d.className, "_execute", "pathOutput not created-" + pathOutput);
            }
            if (TS_FileUtils.isEmptyFile(pathOutput)) {
                d.ce("_execute", "cmd", p.toString());
                TS_FileUtils.deleteFileIfExists(pathOutput);
                return TGS_UnionExcuse.ofExcuse(d.className, "_execute", "pathOutput is empty-" + pathOutput);
            }
            //RETURN
            d.cr("_execute", "returning pathOutput", pathOutput);
            return TGS_UnionExcuse.of(pathOutput);
        }, e -> {
            //HANDLE EXCEPTION
            d.ce("_execute", "HANDLE EXCEPTION...");
            TS_FileUtils.deleteFileIfExists(pathOutput);
            return TGS_UnionExcuse.ofExcuse(e);
        }, () -> TS_FileUtils.deleteFileIfExists(pathConfig));
    }
}

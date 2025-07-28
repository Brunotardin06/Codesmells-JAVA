package org.apache.tools.ant.taskdefs.optional.dotnet;

import org.apache.tools.ant.*;
import org.apache.tools.ant.types.*;

import java.io.File;
import java.util.*;

/* Mantém apenas o smell “refused bequest” da superclasse */
public abstract class DotnetCompile extends DotnetBaseMatchingTask {

    /* ======================= configuração ======================== */
    protected final CompilerConfig cfg = new CompilerConfig();

    /* ---------------- setters públicos ---------------- */
    public void setReferences(String refs)                { cfg.references = refs; }
    public void addReference(FileSet fs)                  { cfg.referenceSets.add(fs); }
    public void addDefine(DotnetDefine def)               { cfg.definitions.add(def); }
    public void addResource(DotnetResource res)           { cfg.resources.add(res); }
    public void setExecutable(String exe)                 { cfg.executable = exe; }
    public void setDebug(boolean flag)                    { cfg.debug = flag; }
    public void setOptimize(boolean flag)                 { cfg.optimize = flag; }
    public void setWarnLevel(int lvl)                     { cfg.warnLevel = lvl; }
    public void setTargetType(TargetTypes t)              { cfg.targetType = t.getValue(); }
    public void setMainClass(String cls)                  { cfg.mainClass = cls; }
    public void setExtraOptions(String opts)              { cfg.extraOptions = opts; }
    public void setAdditionalModules(String m)            { cfg.additionalModules = m; }
    public void setUtf8Output(boolean flag)               { cfg.utf8output = flag; }
    public void setIncludeDefaultReferences(boolean flag) { cfg.includeDefaultRefs = flag; }
    public void setWin32Icon(File f)                      { cfg.win32icon = f; }
    public void setWin32Res(File f)                       { cfg.win32res  = f; }
    public void setFailOnError(boolean b)                 { cfg.failOnError = b; }
    @Override public void setDestDir(File ignored) {
        log("DestDir não é utilizado por DotnetCompile", Project.MSG_VERBOSE);
    }

    /* ========================= execução ========================= */
    @Override public void execute() throws BuildException {
        validate();
        NetCommand cmd = createNetCommand();
        cfg.populate(cmd, this);
        addCompilerSpecificOptions(cmd);          // hook de subclasses
        int outdated = cfg.scanReferenceSets(cmd, getProject(),
                          getOutputFileTimestamp(), getReferenceDelimiter());
        addFilesAndExecute(cmd, outdated > 0);
    }

    protected void validate() {
        if (cfg.executable == null)
            throw new BuildException("Executable não definido");
        if (outputFile != null && outputFile.isDirectory())
            throw new BuildException("destFile não pode ser diretório");
    }

    protected NetCommand createNetCommand() {
        return new NetCommand(this, getTaskName(), cfg.executable);
    }

    /* hooks */
    protected abstract void addCompilerSpecificOptions(NetCommand cmd);
    protected abstract String  getReferenceDelimiter();
    protected abstract String  getFileExtension();
    protected abstract String  createResourceParameter(DotnetResource r);

    /* ========================= config obj ======================== */
    protected static final class CompilerConfig {
        /* parâmetros simples */
        String  executable, references, targetType, mainClass, extraOptions,
                additionalModules;
        int     warnLevel = 3;
        boolean debug = true, optimize, utf8output, includeDefaultRefs = true,
                failOnError = true;
        File    win32icon, win32res;

        /* coleções */
        final Collection<DotnetDefine>   definitions    = new ArrayList<>();
        final Collection<DotnetResource> resources      = new ArrayList<>();
        final Collection<FileSet>        referenceSets  = new ArrayList<>();

        /* popula linha de comando comum */
        void populate(NetCommand c, DotnetCompile owner) {
            c.setFailOnError(failOnError);
            c.addArgument("/nologo");
            addIf(c, str("/addmodule:", additionalModules));
            addIf(c, opt(debug, "/debug+"));
            addIf(c, opt(optimize, "/optimize+"));
            addIf(c, str("/warn:", warnLevel));
            addIf(c, str("/target:", targetType));
            addIf(c, str("/main:",   mainClass));
            addIf(c, str("/out:",    owner.outputFile));
            addIf(c, str("/reference:", references));
            addIf(c, fileArg("/win32icon:", win32icon));
            addIf(c, fileArg("/win32res:",  win32res));
            addIf(c, opt(!includeDefaultRefs, "/nostdlib+"));
            addIf(c, opt(utf8output, "/utf8output"));
            addIf(c, extraOptions);

            String defs = join(definitions, ";", DotnetDefine::getValue);
            addIf(c, defs == null ? null : "/d:" + defs);

            for (DotnetResource r : resources) {
                String p = owner.createResourceParameter(r);
                if (p != null) c.addArgument(p);
            }
        }

        /* varre references */
        int scanReferenceSets(NetCommand cmd, Project p, long outTs, String delim) {
            if (referenceSets.isEmpty()) return 0;
            Set<File> refs = new LinkedHashSet<>();
            for (FileSet fs : referenceSets)
                Collections.addAll(refs,
                        Arrays.stream(fs.getDirectoryScanner(p).getIncludedFiles())
                              .map(f -> new File(fs.getDir(p), f))
                              .toArray(File[]::new));

            int outdated = 0;
            StringJoiner sj = new StringJoiner(delim, "/reference:", "");
            for (File f : refs) {
                if (!isManagedBinary(f)) continue;
                sj.add(f.getPath());
                if (f.lastModified() > outTs) outdated++;
            }
            cmd.addArgument(sj.toString());
            return outdated;
        }

        /* helpers */
        private static boolean isManagedBinary(File f) {
            String n = f.getName().toLowerCase(Locale.ROOT);
            return n.endsWith(".exe") || n.endsWith(".dll") || n.endsWith(".netmodule");
        }
        private static void addIf(NetCommand c, String arg) {
            if (arg != null && !arg.isEmpty()) c.addArgument(arg);
        }
        private static String opt(boolean flag, String txt) { return flag ? txt : null; }
        private static String str(String prefix, Object v)  { return v == null ? null : prefix + v; }
        private static String fileArg(String p, File f)     { return f == null ? null : p + f; }
        private static <T> String join(Collection<T> c, String d,
                                       java.util.function.Function<T,String> fn) {
            return c.isEmpty() ? null
                               : String.join(d, c.stream().map(fn).toArray(String[]::new));
        }
    }

    /* enum de destino */
    public static class TargetTypes extends EnumeratedAttribute {
        @Override public String[] getValues() {
            return new String[] { "exe", "library", "module", "winexe" };
        }
    }
}

package com.ppedregal.typescript.maven;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.JavaScriptException;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.NativeJavaObject;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.commonjs.module.RequireBuilder;
import org.mozilla.javascript.commonjs.module.provider.SoftCachingModuleScriptProvider;

/**
 * Goal which compiles a set of TypeScript files
 *
 * @goal tsc
 *
 * @phase compile
 */
public class TscMojo extends AbstractMojo
{
    /**
     * Output directory for .js compiled files
     * @parameter expression="${ts.targetDirectory}" default-value="target/ts"
     * @required
     */
    private File targetDirectory;

    /**
     * Source directory for .ts source files
     * @parameter expression="${ts.sourceDirectory}" default-value="src/main/ts"
     */
    private File sourceDirectory;

    /**
     * Encoding for files
     * @parameter expression="${project.build.sourceEncoding}
     */
    private String encoding = "utf-8";

    /**
     * Set to true to watch for changes to files and re-compile them on the fly.
     *
     * @parameter expression="${ts.watch}"
     */
    private boolean watch = false;

    /**
     * Set to true to use the command line 'tsc' executable if its on the PATH
     *
     * @parameter expression="${ts.useTsc}"
     */
    private boolean useTsc = false;

    /**
     * Set to true to only use the command line 'tsc' executable and not rely on the built-in tsc compiler code.
     * If this is set to true, the build will fail if the 'tsc' executable is not on the patch
     *
     * @parameter expression="${ts.useTscOnly}"
     */
    private boolean useTscOnly = false;

    /**
     * Set to true to ignore the lib.d.ts by default
     *
     * @parameter expression="${ts.nolib}"
     */
    private boolean noStandardLib = false;

    /**
     * The amount of millis to wait before polling the source files for changes
     *
     * @parameter expression="${ts.pollTime}"
     */
    private long pollTime = 100;

    /**
     * If set, the TypeScript files will be compiled into a single JavaScript file.
     *
     * @parameter expression="${ts.targetFile}
     */
    private File targetFile;

    /**
     * Set the target ECMAScript version for TypeScript compilation output.
     *
     * @parameter expression="${ts.targetVersion}" default-value="ES3"
     */
    private String targetVersion;
    
    /**
     * Set the module format for Type Script compilation output.
     * Acceptable values are "amd" or "commonjs".
     * 
     * @parameter expression="${ts.module}" default-value="amd"
     */
    private String module;

    /**
     * Remove comments in the output JS.
     * 
     * @parameter expression="${ts.removeComments}" default-value="false"
     */
    private boolean removeComments = false;
    
    /**
     * Warning about expression which have a default 'any' type.
     * 
     * @parameter expression="${ts.noImplicitAny}" default-value="false"
     */
    private boolean noImplicitAny = false;
    
    /**
     * Generate corresponding '.d.ts' file.
     *
     * @parameter expression="${ts.declaration}"
     */
    private boolean declaration = false;
    
    /**
     * Set to true to generate source maps.
     *
     * @parameter expression="${ts.sourceMap}" default-value=false
     */
    private boolean sourceMap = false;

    /**
     * Location where the debugger should locate TypeScript files instead
     * of source locations.
     *
     * @parameter expression="${ts.sourceRoot}" default-value=null
     */
    private String sourceRoot = null;
    
    /**
     * Location where the debugger should locate map files instead of
     * generated locations.
     * 
     * @parameter expression="${ts.mapRoot}" default-value=null
     */
    private String mapRoot = null;
    
    /**
     * Set the executable command of tsc program.
     *
     * @parameter expression="${ts.tscExecutable}" default-value="tsc"
     */
    private String tscExecutable;

    private Script nodeScript;
    private Script tscScript;
    private Script fileWedgeScript;
    private ScriptableObject globalScope;
    private boolean watching;

    public void execute() throws MojoExecutionException {
        sourceDirectory.mkdirs();
        targetDirectory.mkdirs();

        try {
            compileScripts();
        } catch (IOException e) {
            throw createMojoExecutionException(e);
        }

        doCompileFiles(false);

        if (watch) {
            watching = true;
            getLog().info("Waiting for changes to " + sourceDirectory + " polling every " + pollTime + " millis");
            checkForChangesEvery(pollTime);
        }
    }

    private void checkForChangesEvery(long ms) throws MojoExecutionException {
        FileSetChangeMonitor monitor = new FileSetChangeMonitor(sourceDirectory, "**/*.ts");
        try {
            while (true) {
                Thread.sleep(ms);
                List<String> modified = monitor.getModifiedFilesSinceLastTimeIAsked();
                if (modified.size() > 0) {
                    // TODO ideally we'd just pass in the files to compile here...
                    doCompileFiles(true);
                }
            }
        } catch (InterruptedException e) {
            getLog().info("Caught interrupt, quitting.");
        }
    }

    private void doCompileFiles(boolean checkTimestamp) throws MojoExecutionException {
        Collection<File> files = FileUtils.listFiles(sourceDirectory, new String[] {"ts"}, true);
        if (targetFile != null) {
            doCompileAllFiles(checkTimestamp, files);
        } else {
            doCompileSingleFiles(checkTimestamp, files);
        }
    }

    private void doCompileAllFiles(boolean checkTimestamp, Collection<File> files) throws MojoExecutionException {
        try {
            if (!watching) {
                getLog().info("Searching directory " + sourceDirectory.getCanonicalPath());
            }
            List<String> params = new ArrayList<String>();
            params.add("--out");
            params.add(targetFile.getPath());

            // find the latest modification among the source files
            // and add files to an ASCII file
            File modifiedFilesFile = new File("modifiedFiles.txt");
            PrintWriter writer = new PrintWriter(modifiedFilesFile, "UTF-8");

            Long lastModified = 0l;
            for (File file : files) {
                if (file.lastModified() > lastModified) {
                    lastModified = file.lastModified();
                }
                writer.println(file.getPath());
            }
            writer.close();

            // add the ASCII file as @<file> parameter
            params.add("@"+"modifiedFiles.txt");

            if (!targetFile.exists() || !checkTimestamp || lastModified > targetFile.lastModified()) {
                try {
                    tsc(params.toArray(new String[] {}));
                } catch (TscInvocationException e) {
                    getLog().error(e.getMessage());
                    if (getLog().isDebugEnabled()) {
                        getLog().debug(e);
                    }
                }
            }
        } catch (IOException e) {
            throw createMojoExecutionException(e);
        }
    }

    private void doCompileSingleFiles(boolean checkTimestamp, Collection<File> files)
            throws MojoExecutionException {
        try {
            int compiledFiles = 0;
            if (!watching) {
                getLog().info("Searching directory " + sourceDirectory.getCanonicalPath());
            }
            for (File file : files) {
                try {
                    String sourcePath = file.getPath().substring(sourceDirectory.getPath().length());
                    String targetPath = FilenameUtils.removeExtension(sourcePath) + ".js";
                    File sourceFile = new File(sourceDirectory, sourcePath).getAbsoluteFile();
                    File targetFile = new File(targetDirectory, targetPath).getAbsoluteFile();
                    if (!targetFile.exists() || !checkTimestamp || sourceFile.lastModified() > targetFile.lastModified()) {
                        String sourceFilePath = sourceFile.getPath();
                        getLog().info(String.format("Compiling: %s", sourceFilePath));
                        String generatePath = targetFile.getParentFile().getPath();
                        tsc("--outDir", generatePath, sourceFilePath);
                        getLog().info(String.format("Generated: %s", targetFile));
                        compiledFiles++;
                    }
                } catch (TscInvocationException e) {
                    getLog().error(e.getMessage());
                    if (getLog().isDebugEnabled()) {
                        getLog().debug(e);
                    }
                    throw new MojoExecutionException(e.getMessage(), e);
                }
            }
            if (compiledFiles == 0) {
                getLog().info("Nothing to compile");
            } else {
                getLog().info(String.format("Compiled %s file(s)", compiledFiles));
            }
        } catch (IOException e) {
            throw createMojoExecutionException(e);
        }
    }

    private void compileScripts() throws IOException {
        try {
            Context.enter();
            Context ctx = Context.getCurrentContext();
            ctx.setOptimizationLevel(-1);
            globalScope = ctx.initStandardObjects();
            RequireBuilder require = new RequireBuilder();
            require.setSandboxed(false);
            require.setModuleScriptProvider(new SoftCachingModuleScriptProvider(new ClasspathModuleSourceProvider()));
            require.createRequire(ctx, globalScope).install(globalScope);
            nodeScript = compile(ctx,"node.js");
            fileWedgeScript = compile(ctx, "filewedge.js");
            tscScript = compile(ctx,"tsc.js");
        } finally {
            Context.exit();

        }
        Context.enter();
    }

    private Script compile(Context context,String resource) throws IOException {
        InputStream stream =  TscMojo.class.getClassLoader().getResourceAsStream(resource);
        if (stream==null){
            throw new FileNotFoundException("Resource open error: "+resource);
        }
        try {
            return context.compileReader(new InputStreamReader(stream), resource, 1, null);
        } catch (IOException e){
            throw new IOException("Resource read error: "+resource);
        } finally {
            try {
                stream.close();
            } catch (IOException e){
                throw new IOException("Resource close error: "+resource);
            }
            stream = null;
        }
    }
    
    private List<String> optionArguments() {
        List<String> options = new ArrayList<String>();

        if (declaration) {
            options.add("--declaration");
        }
        
        if (noStandardLib) {
            options.add("--nolib");
        }
        
        if (removeComments) {
            options.add("--removeComments");
        }
        
        if (noImplicitAny) {
            options.add("--noImplicitAny");
        }
        
        if (sourceMap) {
            options.add("--sourceMap");
            if (sourceRoot != null) {
                options.add("--sourceRoot");
                options.add(sourceRoot);
            }
            if (mapRoot != null) {
                options.add("--mapRoot");
                options.add(mapRoot);
            }
        }
        
        if (targetVersion != null) {
            options.add("--target");
            options.add(targetVersion);
        }

        if (module != null) {
            options.add("--module");
            options.add(module);
        }
        return options;
    }    
        
    private void tsc(String...args) throws TscInvocationException, MojoExecutionException {
        if (useTscBinary(args)) {
            return;
        }

        try {
            Context.enter();
            Context ctx = Context.getCurrentContext();

            nodeScript.exec(ctx, globalScope);
            fileWedgeScript.exec(ctx, globalScope);
            
            NativeObject proc = (NativeObject)globalScope.get("process");
            NativeArray argv = (NativeArray)proc.get("argv");
            argv.defineProperty("length", 0, ScriptableObject.EMPTY);
            int i = 0;
            argv.put(i++, argv, "node");
            argv.put(i++, argv, "tsc.js");
            
            for (String option : optionArguments()) {
                argv.put(i++, argv, option);
            }

            for (String s:args){
                argv.put(i++, argv, s);
            }

            proc.defineProperty("encoding", encoding, ScriptableObject.READONLY);

            NativeObject mainModule = (NativeObject)proc.get("mainModule");
            mainModule.defineProperty("filename", "___classloader_resource___/tsc.js", ScriptableObject.READONLY);
            
            getLog().info("Using Rhino JS Engine to run command: " + nativeArrayToString(argv));

            tscScript.exec(ctx,globalScope);
        } catch (JavaScriptException e){
            if (e.getValue() instanceof NativeJavaObject){
                NativeJavaObject njo = (NativeJavaObject)e.getValue();
                Object o = njo.unwrap();
                if (o instanceof ProcessExit){
                    ProcessExit pe = (ProcessExit)o;
                    if (pe.getStatus()!=0){
                        throw new TscInvocationException("Process Error: "+pe.getStatus(), e);
                    }
                } else {
                    throw new TscInvocationException("JavaScript Error: " + e.details() +  "\nJS stack:\n"+e.getScriptStackTrace(), e);
                }
            } else {
                throw new TscInvocationException("JavaScript Error: " + e.details() +  "\nJS stack:\n"+e.getScriptStackTrace(), e);
            }
        } catch (RhinoException e){
            getLog().error(e.getMessage());
            throw new TscInvocationException("Rhino Error",e);
        } finally {
            org.mozilla.javascript.Context.exit();
        }
    }
    
    /**
     * Format a native array of strings into one space separated string.
     */
    private static String nativeArrayToString(NativeArray narray) {
        StringBuilder sb = new StringBuilder();
        for (Object narray1 : narray) {
            sb.append(narray1.toString());
            sb.append(" ");
        }
        return sb.toString();
    }

    private boolean useTscBinary(String[] args) throws MojoExecutionException {
        if (useTsc) {

            // lets try execute the 'tsc' executable directly
            List<String> arguments = new ArrayList<String>();
            // add command name as argument, "tsc" is default value
            if(getTscExecutable()!=null && getTscExecutable().length()>0) {
                arguments.add(getTscExecutable());
            }
            else {
                arguments.add("tsc");
            }
            arguments.addAll(optionArguments());
            arguments.addAll(Arrays.asList(args));
            
            getLog().info("Using external nodejs to run command: " + stringListToString(arguments));

            getLog().debug("About to execute command: " + arguments);
            ProcessBuilder builder = new ProcessBuilder(arguments);
            try {
                Process process = builder.start();
                redirectOutput(process.getInputStream(), false);
                redirectOutput(process.getErrorStream(), true);

                int value = process.waitFor();
                if (value != 0) {
                    getLog().error("Failed to execute tsc. Return code: " + value);
                } else {
                    getLog().debug("Compiled file successfully");
                }
            } catch (IOException e) {
                if (useTscOnly) {
                    getLog().error("Failed to execute tsc: " + e);
                    throw createMojoExecutionException(e);
                } else {
                    getLog().debug("IOException while running 'tsc' binary", e);
                    getLog().info("Unable to run 'tsc' binary - falling back to internal tsc compiler code");
                    return false;
                }
            } catch (InterruptedException e) {
                throw new MojoExecutionException(e.getMessage());
            }
            return true;
        }
        return false;
    }
    
    /**
     * Format a list of strings into one space separated string.
     */
    private static String stringListToString(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (Object narray1 : list) {
            sb.append(narray1.toString());
            sb.append(" ");
        }
        return sb.toString();
    }

    private void redirectOutput(InputStream is, boolean error) throws IOException {
        try {
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);
            while (true) {
                String line = br.readLine();
                if (line == null) {
                    break;
                }
                if (error) {
                    getLog().error(line);
                } else {
                    getLog().info(line);
                }
            }
        } finally {
            is.close();
        }
    }

    private MojoExecutionException createMojoExecutionException(IOException e) {
        return new MojoExecutionException(e.getMessage());
    }

    public File getTargetDirectory() {
        return targetDirectory;
    }

    public void setTargetDirectory(File targetDirectory) {
        this.targetDirectory = targetDirectory;
    }

    public File getSourceDirectory() {
        return sourceDirectory;
    }

    public void setSourceDirectory(File sourceDirectory) {
        this.sourceDirectory = sourceDirectory;
    }

    public File getTargetFile() {
        return targetFile;
    }
    
    public void setTargetFile(File targetFile) {
        this.targetFile = targetFile;
    }
    
    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public boolean getNoStandardLib() {
        return noStandardLib;
    }
    
    public void setNoStandardLib(boolean noStandardLib) {
        this.noStandardLib = noStandardLib;
    }
    
    public String getModule() {
        return module;
    }
    
    public void setModule(String module) {
        this.module = module;
    }
    
    public boolean getRemoveComments() {
        return removeComments;
    }
    
    public void setRemoveComments(boolean removeComments) {
        this.removeComments = removeComments;
    }
    
    public boolean getNoImplicitAny() {
        return noImplicitAny;
    }
    
    public void setNoImplicitAny(boolean noImplicitAny) {
        this.noImplicitAny = noImplicitAny;
    }
    
    public boolean getDeclaration() {
        return declaration;
    }

    public void setDeclaration(boolean declaration) {
        this.declaration = declaration;
    }
    
    public boolean getSourceMap() {
        return sourceMap;
    }
    
    public void setSourceMap(boolean sourceMap) {
        this.sourceMap = sourceMap;
    }
    
    public String getSourceRoot() {
        return sourceRoot;
    }
    
    public void setSourceRoot(String sourceRoot) {
        this.sourceRoot = sourceRoot;
    }

    public String getMapRoot() {
        return mapRoot;
    }
    
    public void setMapRoot(String mapRoot) {
        this.mapRoot = mapRoot;
    }
    
    public String getTscExecutable() {return tscExecutable;}
    public void setTscExecutable(String tscExecutable) {this.tscExecutable = tscExecutable;}

}

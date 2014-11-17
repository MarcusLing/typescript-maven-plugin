<h1>TypeScript Maven Plugin</h1>
Maven plugin that integrates <a href="http://typescript.codeplex.com/">TypeScript</a> compiler into maven builds. Supports v1.1 of the TypeScript compiler and can use automatically use the Rhino JavaScript engine if the 'tsc' command is not available.

To use the plugin in maven you need to follow these steps:



~~1) Add the following plugin repository to your pom.xml~~
TODO: publish this plugin to a repository.

<pre>
    &lt;pluginRepository&gt;
      &lt;id&gt;typescript-maven-plugin&lt;/id&gt;
      &lt;url&gt;https://raw.github.com/ppedregal/typescript-maven-plugin/master/repo&lt;/url&gt;
    &lt;/pluginRepository&gt;
</pre>

2) Add the following build plugin to your pom.xml. The options are directly derived from the compiler's command line options:

<pre>
      &lt;plugin&gt;
        &lt;groupId&gt;com.ppedregal.typescript&lt;/groupId&gt;
      	&lt;artifactId&gt;typescript-maven-plugin&lt;/artifactId&gt;        
        &lt;configuration&gt;
        	&lt;sourceDirectory&gt;src/main/ts&lt;/sourceDirectory&gt;
        	&lt;targetDirectory&gt;target/ts&lt;/targetDirectory&gt;

            &lt;!-- 'amd' or 'commonjs' --&gt;
        	&lt;module&gt;amd&lt;/module&gt;

            &lt;!-- Defaults to false --&gt;
            &lt;noStandardLib&gt;false&lt;/noStandardLib&gt;

            &lt;!-- 'ES3' or 'ES5'. Defaults to ES3 --&gt;
            &lt;targetVersion&gt;ES5&lt;/targetVersion&gt;

            &lt;!-- true or false. Defaults to false --&gt;
            &lt;removeComments&gt;false&lt;/removeComments&gt;

            &lt;!-- true or false. Defaults to false --&gt;
            &lt;noImplicitAny&gt;false&lt;/noImplicitAny&gt;

            &lt;!-- true or false. Defaults to false --&gt;
            &lt;declaration&gt;false&lt;/declaration&gt;

            &lt;!-- true or false. Defaults to false --&gt;
            &lt;sourceMap&gt;false&lt;/sourceMap&gt;

            &lt;!-- (Optional) Source root path. --&gt;
            &lt;sourceRoot&gt;...&lt;/sourceRoot&gt;

            &lt;!-- (Optional) Map root path. --&gt;
            &lt;mapRoot&gt;...&lt;/mapRoot&gt;

        &lt;/configuration&gt;
        &lt;executions&gt;
            &lt;execution&gt;
                &lt;phase&gt;compile&lt;/phase&gt;
                &lt;goals&gt;
                    &lt;goal&gt;tsc&lt;goal&gt;
                &lt;/goals&gt;
            &lt;/execution&gt;
        &lt;/executions&gt;
      &lt;/plugin&gt;
</pre>

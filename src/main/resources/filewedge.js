var ___fs = require('fs');

(function(){
  var fs = ___fs;
  var fs_existsSync = fs.existsSync.bind(fs);
  var resourcePrefix = "___classloader_resource___/";
  
  function wedgeExistsSync(filename) {
    if (filename.indexOf(resourcePrefix) === 0) {
      var filepart = filename.substr(resourcePrefix.length);
      var stream = java.lang.Class.forName("com.ppedregal.typescript.maven.TscMojo").getClassLoader().getResourceAsStream(filepart);
      if (stream === null) {
        return false;
      } else {
        stream.close();
        return true;
      }
    } else {
      return fs_existsSync(filename);
    }    
  }
  fs.existsSync = wedgeExistsSync;
  
  var fs_readFileSync = fs.readFileSync.bind(fs);
  function wedgeReadFileSync(filename, enc) {
    enc = enc || process.encoding || "utf-8";

    if (filename.indexOf(resourcePrefix) === 0) {
      var filepart = filename.substr(resourcePrefix.length);
      var stream = java.lang.Class.forName("com.ppedregal.typescript.maven.TscMojo").getClassLoader().getResourceAsStream(filepart);
      if (stream === null) {
        return null;
      }
      
      var inputstreamreader = new java.io.InputStreamReader(stream, enc);
      var reader = new java.io.BufferedReader(inputstreamreader);
      try {
          var buffer = new java.lang.StringBuffer();
          var line;
          while ((line=reader.readLine()) !== null){
              buffer.append(line);
              buffer.append("\r\n");
          }
      } finally {
          reader.close();
      }
      reader = null;
      return {
          "0":0,
          "1":0,
          length: buffer.length(),
          toString:function(){
              return new String(buffer.toString());
          }
      };
      
    } else {
      return fs_readFileSync(filename, enc);
    }
  }
  fs.readFileSync = wedgeReadFileSync;  

})();

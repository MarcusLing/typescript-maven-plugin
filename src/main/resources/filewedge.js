var ___fs = require('fs');

(function(){
  var fs = ___fs;
  var fs_existsSync = fs.existsSync.bind(fs);
  var resourcePrefix = "___classloader_resource___/";
  
  function wedgeExistsSync(filename) {
    java.lang.System.err.println("!!!!!!!!!! Wedged existsSync(" + filename + ") !!!!!!!!!!!");
  try {  
    if (filename.indexOf(resourcePrefix) === 0) {
      var filepart = filename.substr(resourcePrefix.length);
      var stream = java.lang.Class.forName("com.ppedregal.typescript.maven.TscMojo").getClassLoader().getResourceAsStream(filepart);
      if (stream === null) {
    java.lang.System.err.println("!!!!!!!!!! Wedged existsSync reurning stream false");
        return false;
      } else {
        stream.close();
    java.lang.System.err.println("!!!!!!!!!! Wedged existsSync reurning stream true");
        return true;
      }
    } else {
      return fs_existsSync(filename);
    }
    
  } catch (e) {
    java.lang.System.err.println(e);
  }
  }
  fs.existsSync = wedgeExistsSync;
  
  var fs_readFileSync = fs.readFileSync.bind(fs);
  function wedgeReadFileSync(filename, enc) {
    java.lang.System.err.println("!!!!!!!!!! Wedged readFileSync(" + filename + ") !!!!!!!!!!!");
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
//    java.lang.System.err.println("!!!!!!!!!! Wedged readFileSync(" + filename + ") CHECKPOINT 1.3");
//      } catch (e){
//        java.lang.System.err.println(e);
//    java.lang.System.err.println("!!!!!!!!!! Wedged readFileSync(" + filename + ") CHECKPOINT 2");
//          return null;
      } finally {
          reader.close();
      }
//    java.lang.System.err.println("!!!!!!!!!! Wedged readFileSync(" + filename + ") CHECKPOINT 3");
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

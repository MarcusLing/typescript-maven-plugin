exports.EOL = (function() {
    return java.lang.System.getProperty("line.separator");
})();
exports.platform = function() {
  var osArch = java.lang.System.getProperty("os.arch");
  var osName = java.lang.System.getProperty("os.name", "generic").toLowerCase(java.util.Locale.ENGLISH);
  if (osName.indexOf("mac") >= 0 || osName.indexOf("darwin") >= 0) {
    return "darwin";
  } else if (osName.indexOf("win") >= 0) {
    return osArch.indexOf("64") >= 0 ? "win64" : "win32";
  } else {
    return osName;
  }
};


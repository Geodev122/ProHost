#!/usr/bin/env node
/* Builds dist/control-room.html (the IG Control Room artifact) from src/. Usage: node tools/control-room/build.js */
const fs = require("fs");
const path = require("path");
const src = (f) => fs.readFileSync(path.join(__dirname, "src", f), "utf8");
const logo = src("logo.b64").trim();
const js = ["engine.js", "templates.js", "sheet.js", "views.js", "app.js"].map(src).join("\n").replace(/__LOGO__/g, logo);
const html = `<title>IG Control Room</title>
<meta name="claude:viewport-fit-sides" content="cover">
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@600;700;800&family=Inter:wght@400;500;600;700&display=swap">
<style>
${src("style.css")}
</style>
<div id="app"></div>
<script>
(function(){
${js}
})();
</script>
`;
fs.mkdirSync(path.join(__dirname, "dist"), { recursive: true });
fs.writeFileSync(path.join(__dirname, "dist", "control-room.html"), html);
console.log(`dist/control-room.html ${(html.length / 1024).toFixed(1)} KB`);

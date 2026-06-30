import fs from "node:fs/promises";
import { FileBlob, SpreadsheetFile } from "@oai/artifact-tool";

const files = [
  "D:/桌面/大三下/小学期/JoyGather/.codex-output/testcases/outputs/JoyGather_华为云_手工测试用例_20260630.xlsx",
  "D:/桌面/大三下/小学期/JoyGather/.codex-output/testcases/outputs/JoyGather_华为云_API接口测试用例_20260630.xlsx",
];

for (const file of files) {
  try {
    const input = await FileBlob.load(file);
    const workbook = await SpreadsheetFile.importXlsx(input);
    const sheetInfo = await workbook.inspect({ kind: "sheet", include: "id,name", maxChars: 1000 });
    console.log(`OK import: ${file}`);
    console.log(sheetInfo.ndjson);
    const preview = await workbook.render({ sheetName: workbook.worksheets.getItemAt(0).name, range: "A1:K12", scale: 1, format: "png" });
    const out = file.replace(/\.xlsx$/, ".preview.png");
    await fs.writeFile(out, new Uint8Array(await preview.arrayBuffer()));
    console.log(`OK render: ${out}`);
  } catch (error) {
    console.log(`RENDER_SKIPPED: ${file}`);
    console.log(error?.message ?? String(error));
  }
}

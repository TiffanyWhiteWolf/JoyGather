import { FileBlob, SpreadsheetFile } from "@oai/artifact-tool";

const files = [
  "D:/Downloads/Import-Testcase-Template-20260518195244.xlsx",
  "D:/Downloads/importTemplate-20260630094947.xlsx",
];

for (const file of files) {
  console.log(`\n=== ${file} ===`);
  const input = await FileBlob.load(file);
  const workbook = await SpreadsheetFile.importXlsx(input);
  const summary = await workbook.inspect({
    kind: "workbook,sheet,table,region,computedStyle",
    maxChars: 12000,
    tableMaxRows: 12,
    tableMaxCols: 20,
    tableMaxCellChars: 120,
  });
  console.log(summary.ndjson);
}

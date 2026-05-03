import assert from "node:assert/strict";
import { resolveApiRoot } from "../src/api/runtime/ApiEndpointRegistry";

const cases = [
  {
    name: "默认回退到 /api",
    actual: resolveApiRoot("", ""),
    expected: "/api",
  },
  {
    name: "兼容旧 /api/netease",
    actual: resolveApiRoot("", "/api/netease"),
    expected: "/api",
  },
  {
    name: "兼容新的 /splayer/netease",
    actual: resolveApiRoot("", "https://example.com/splayer/netease"),
    expected: "https://example.com/splayer",
  },
  {
    name: "优先使用 VITE_API_ROOT",
    actual: resolveApiRoot("https://example.com/splayer", "http://legacy/api/netease"),
    expected: "https://example.com/splayer",
  },
];

for (const testCase of cases) {
  assert.equal(testCase.actual, testCase.expected, testCase.name);
}

console.log("API endpoint registry checks passed.");

import { readFileSync } from "node:fs";

const workflowPath = ".github/workflows/android-native-debug.yml";
const workflow = readFileSync(workflowPath, "utf8");

const releaseMappings = [
  ["feat", "新增"],
  ["fix", "修复"],
  ["docs", "文档"],
  ["style", "样式"],
  ["refactor", "重构"],
  ["perf", "性能"],
  ["test", "测试"],
  ["build", "构建"],
  ["ci", "CI"],
  ["chore", "维护"],
  ["revert", "回退"],
];

const requiredPatterns = [
  ...releaseMappings.map(([type, label]) => ({
    description: `使用与桌面 release 一致的 ${type} 分类分组`,
    pattern: type === "chore" ? 'chore) type="维护"' : `${type}) type="${label}"`,
  })),
  {
    description: "使用 commit_subjects.txt 作为提交信息来源",
    pattern: /commit_subjects\.txt/,
  },
  {
    description: "生成 changelog_content.md 作为分组更新内容",
    pattern: /append_section "新增"[\s\S]*append_section "修复"[\s\S]*changelog_content\.md/,
  },
  {
    description: "release notes 包含新增分类标题",
    pattern: /printf '### %s\\n' "\$1" >> changelog_content\.md/,
  },
  {
    description: "release notes 包含修复分类标题",
    pattern: /append_section "修复"/,
  },
  {
    description: "release body 包含更新内容标题",
    pattern: /## 更新内容/,
  },
  {
    description: "release body 包含 Version 字段",
    pattern: /- Version: \${APK_VERSION}/,
  },
  {
    description: "release body 包含 Commit 字段",
    pattern: /- Commit: \${GITHUB_SHA}/,
  },
  {
    description: "release body 包含 Run 字段",
    pattern:
      /- Run: \${GITHUB_SERVER_URL}\/\${GITHUB_REPOSITORY}\/actions\/runs\/\${GITHUB_RUN_ID}/,
  },
  {
    description: "release body 包含 APK 文件名字段",
    pattern: /- APK: \${APK_NAME}/,
  },
  {
    description: "tag 包含 run id，保证每次 push 追加唯一 release",
    pattern:
      /tag_name="android-native-debug-\${GITHUB_RUN_ID}-\${GITHUB_RUN_ATTEMPT}-\${GITHUB_SHA::7}"/,
  },
];

const failures = requiredPatterns.filter(({ pattern }) => {
  if (typeof pattern === "string") {
    return !workflow.includes(pattern);
  }

  return !pattern.test(workflow);
});

if (failures.length > 0) {
  console.error(`${workflowPath} 未满足 Android native debug release 约束：`);
  for (const failure of failures) {
    console.error(`- ${failure.description}`);
  }
  process.exit(1);
}

console.log(`${workflowPath} Android native debug release 约束检查通过`);

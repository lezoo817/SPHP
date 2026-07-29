const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');
const lucide = require('lucide');

const prototypeDirectory = __dirname;
const sourcePath = path.join(prototypeDirectory, 'prototype.html');
const outputDirectory = path.join(prototypeDirectory, 'output');

/**
 * 将 Lucide 图标节点转换为可嵌入页面的 SVG 字符串。
 *
 * @param {string} iconName Lucide 图标名称
 * @returns {string} SVG 字符串
 * @throws {Error} 图标名称不存在时抛出异常
 */
function createIconSvg(iconName) {
  const iconNodes = lucide[iconName];
  if (!iconNodes) {
    throw new Error(`未找到 Lucide 图标：${iconName}`);
  }

  // 根据 Lucide 节点定义生成统一线性图标，避免原型内混用不同风格资源。
  const children = iconNodes.map(([tagName, attributes]) => {
    const attributeText = Object.entries(attributes)
      .map(([key, value]) => `${key}="${String(value)}"`)
      .join(' ');
    return `<${tagName} ${attributeText}></${tagName}>`;
  }).join('');

  return `<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${children}</svg>`;
}

/**
 * 替换原型 HTML 中的图标占位节点。
 *
 * @param {string} html 原始 HTML 内容
 * @returns {string} 已嵌入 SVG 图标的 HTML 内容
 */
function injectIcons(html) {
  return html.replace(/<span class="icon([^\"]*)" data-icon="([^\"]+)"><\/span>/g, (_, extraClasses, iconName) => {
    return `<span class="icon${extraClasses}">${createIconSvg(iconName)}</span>`;
  });
}

/**
 * 渲染全部手机端页面并分别保存为 PNG。
 *
 * @returns {Promise<void>} 渲染完成后结束
 */
async function renderPrototypes() {
  // 使用 UTF-8 读取包含中文的原型源文件，确保导出图片文字不乱码。
  const sourceHtml = fs.readFileSync(sourcePath, 'utf8');
  const html = injectIcons(sourceHtml);
  fs.mkdirSync(outputDirectory, { recursive: true });

  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1600, height: 1000 }, deviceScaleFactor: 2 });
  await page.setContent(html, { waitUntil: 'load' });
  await page.evaluate(() => document.fonts.ready);

  const pageNames = ['home', 'assistant', 'pharmacy', 'mine'];
  for (const pageName of pageNames) {
    // 对单个手机画布截图，避免把灰色预览背景写入交付图片。
    const prototype = page.locator(`[data-page="${pageName}"]`);
    await prototype.screenshot({
      path: path.join(outputDirectory, `${pageName}.png`),
      type: 'png',
    });
  }

  await browser.close();
}

renderPrototypes().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});

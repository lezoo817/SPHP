from pathlib import Path

from PIL import Image, ImageStat


PROTOTYPE_DIRECTORY = Path(__file__).resolve().parent
OUTPUT_DIRECTORY = PROTOTYPE_DIRECTORY / "output"
EXPECTED_PAGES = {
    "home.png": ["智愈先锋", "预约挂号", "用药提醒"],
    "assistant.png": ["就诊助手", "报到候诊", "退号记录"],
    "pharmacy.png": ["我的处方", "全部订单", "附近有货药店"],
    "mine.png": ["健康档案", "随访计划", "账号与安全"],
}


def verify_prototypes() -> None:
    """
    校验四张原型图的文件、尺寸、画面像素及关键业务文案。

    Returns:
        None

    Raises:
        AssertionError: 原型文件缺失、尺寸错误、画面空白或内容不符合要求时抛出
    """
    # 使用 UTF-8 读取原型源文件，确保中文关键文案按原始编码校验。
    html = (PROTOTYPE_DIRECTORY / "prototype.html").read_text(encoding="utf-8")
    assert "HRMS" not in html and "员工" not in html and "考勤" not in html, "发现其他项目内容"

    for filename, keywords in EXPECTED_PAGES.items():
        image_path = OUTPUT_DIRECTORY / filename
        assert image_path.is_file(), f"缺少文件：{filename}"
        assert image_path.stat().st_size > 100_000, f"文件异常偏小：{filename}"

        # 检查导出尺寸与像素变化，排除空白图或错误画布。
        with Image.open(image_path) as image:
            assert image.size == (750, 1624), f"尺寸错误：{filename} {image.size}"
            extrema = ImageStat.Stat(image.convert("RGB")).extrema
            assert any(low != high for low, high in extrema), f"图片为空白：{filename}"

        # 关键文案来自各页面需求，用于防止页面错配或误用其他项目模板。
        for keyword in keywords:
            assert keyword in html, f"{filename} 缺少关键文案：{keyword}"

        print(
            f"PASS {filename}: 750x1624, {image_path.stat().st_size} bytes, "
            f"keywords={'/'.join(keywords)}"
        )

    print("PASS total=4, unrelated_project_terms=0")


if __name__ == "__main__":
    verify_prototypes()

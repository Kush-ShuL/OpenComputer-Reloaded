# 屏幕文字与字体

默认字体使用 OpenComputers 原有的 Unifont HEX 数据，可以显示中文和其他 Unicode 字符。双宽字符占两个文字格。旧版 CP437 贴图字体可以通过 client.fontRenderer=texture 选择；它不包含的字符沿用原版问号回退，不替换为 Minecraft 的字体。

原字体格尺寸在默认 HEX 模式为 4×8，在贴图模式为 5×9。屏幕界面和世界中的屏幕均按文字格及视口比例缩放。贴图字体保留抗锯齿图片选择、fontCharScale 和 MIN 过滤设置，MAG 仍使用最近邻。文字和背景使用实际屏幕颜色与调色板，保持原有透明混合和不写深度的行为。

行为与素材仅来自 MightyPirates/OpenComputers 提交
258943363ea8fc4ef14948e6bc6f67375061580c，主要包括原 TextureFontRenderer、
DynamicFontRenderer、StaticFontRenderer、FontParserHex、TextBufferRenderCache、
gui/BufferRenderer、文字缓冲与显存栅格实现。字体、PNG 和原许可署名保持不变，
不使用社区移植或生成的新字体素材。

## 有限客户端验收

三档 GPU 的容量、能耗、预算、裁剪和保存已经由 advanced-20261027-e2 验证，
见[显卡与显存](graphics-card.md)。TextClientContract 复用这些结果，集中验证
同一个真实机箱、显卡、屏幕和键盘在正常客户端中的显示：

- 32×12 缓冲和 24×10 视口包含 ASCII、带音符字母、希腊字母和中文双宽字符，
  以及真实前背景、调色板、纵写、复制、填充和公开 raw 二维写入。真实屏幕交互
  打开原界面并接收全快照，随后实际客户端的字符、颜色、调色板和视口与服务器一致。
- 显卡实际分配 6×3 显存页并写入，经 bitblt 更新屏幕。正常增量包同时更新已有
  客户端缓冲和真实显存缓存；释放显存后缓存删除，已拷贝的屏幕内容保留。
- 配置通过实际 HOCON 加载切换 HEX 和 CP437 字体、抗锯齿图片及过滤设置，
  再执行 Minecraft 正常资源重载。原屏幕对象和内容保持，实际帧像素检查背景色、
  中文双格或问号回退，以及原生纹理的 MIN/MAG 参数。
- 正常 GUI 与世界屏幕各保留实际 PNG。世界中四格宽、一行高的色块通过真实
  帧缓冲像素验证字体比例；截图由统一 Windows 客户端运行后审阅。

合同使用实际 ClientLevel、GPU 回调、公开文字缓冲和正常 ScreenGui/world renderer，
不构造替代 GuiGraphics、文字缓冲或渲染提供者。读取布局、纹理和客户端显存仅用于
观察实际结果；临时配置和相机在结束时恢复。C19 的远程传输和其他共用验收仍独立记录。

Windows 实测通过以下两次运行的证据并集，完成 C21 的有限客户端验收：

- [`client-text-20261027-k3`](../build/api-compat-addon/integration-client/client-text-20261027-k3/client-results.txt)
  已通过全快照、增量、显存分配/复制/释放、资源重载和两种字体的 GUI 像素检查。
  `text-vram-incremental.png` 和 `text-cp437-reloaded.png` 已人工审阅。
  该次整体报告仍为 FAIL：其世界截图只有天空和草地，没有拍到测试屏幕，
  不作为世界渲染证据。
- [`client-text-world-20261028-l1`](../build/api-compat-addon/integration-client/client-text-world-20261028-l1/client-results.txt)
  整体 **PASS**。`ocfixture.textFocus=world` 复用上述已通过部分，集中补验两种
  原字体的世界显示。每次拍摄前通过正常服务器传送把玩家放回屏幕前，等待
  真实相机到位且准星命中屏幕后再截图。实际 CP437 与 HEX 世界帧中的屏幕、
  字符和色条均已人工审阅；原生四格色块测得 24×11 像素，符合对应字体比例。

补充运行同时验证字体重载没有丢失实际 GPU 栅格。修正只涉及测试相机与
截取条件，未为通过验收修改生产文字渲染器。上述证据覆盖本页固定的
GUI/世界字体、颜色和显存显示范围；远程连接恢复继续由 C19 验收。

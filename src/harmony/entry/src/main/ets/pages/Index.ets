import { webview } from '@kit.ArkWeb';
import { fileIo } from '@kit.CoreFileKit';
import { common } from '@kit.AbilityKit';
import { BusinessError } from '@kit.BasicServicesKit';

@Entry
@Component
struct Index {
  controller: webview.WebviewController = new webview.WebviewController();
  @State prog: number = 0;

  aboutToAppear(): void {
    webview.WebviewController.setWebDebuggingAccess(true);
    this.controller.setWebDownloadDelegate({
      onDownloadStart: (event: webview.WebDownloadItem) => {
        this.saveFile(event);
      }
    });
  }

  saveFile(item: webview.WebDownloadItem): void {
    try {
      const ctx = getContext(this) as common.UIAbilityContext;
      const dir = ctx.filesDir + '/downloads';
      if (!fileIo.accessSync(dir)) {
        fileIo.mkdirSync(dir, true);
      }
      const path = dir + '/' + decodeURIComponent(item.getSuggestedFileName() || ('agnes_' + Date.now()));
      fileIo.open(path, fileIo.OpenMode.CREATE | fileIo.OpenMode.READ_WRITE).then((f) => {
        this.controller.startDownload(item, f.fd).then(() => {
          console.info('下载完成 ' + path);
          f.close();
        }).catch((e: BusinessError) => {
          console.error('下载失败 ' + JSON.stringify(e));
        });
      });
    } catch (e) {
      console.error('保存异常 ' + JSON.stringify(e));
    }
  }

  build() {
    Column() {
      if (this.prog < 100) {
        Progress({ value: this.prog, total: 100, type: ProgressType.Linear })
          .width('100%')
      }
      Web({ src: $rawfile('index.html'), controller: this.controller })
        .javaScriptAccess(true)
        .domStorageAccess(true)
        .fileAccess(true)
        .imageAccess(true)
        .onlineImageAccess(true)
        .mixedMode(MixedMode.All)
        .zoomAccess(true)
        .databaseAccess(true)
        .onPageEnd(() => { this.prog = 100; })
        .width('100%')
        .height('100%')
        .backgroundColor('#F7F9F8')
    }
    .width('100%')
    .height('100%')
  }
}

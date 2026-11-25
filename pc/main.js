const { app, BrowserWindow, shell, Menu } = require('electron');
const path = require('path');

function createWin() {
  const win = new BrowserWindow({
    width: 1180,
    height: 840,
    minWidth: 880,
    minHeight: 620,
    backgroundColor: '#0d1220',
    title: 'Agnes 工作台',
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: false,
      nodeIntegration: false,
      spellcheck: false
    }
  });

  win.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https?:/i.test(url)) shell.openExternal(url);
    return { action: 'deny' };
  });

  win.webContents.on('will-navigate', (e, url) => {
    if (!/^file:/i.test(url)) {
      e.preventDefault();
      shell.openExternal(url);
    }
  });

  win.loadFile(path.join(__dirname, 'app', 'index.html'));
  return win;
}

app.whenReady().then(() => {
  Menu.setApplicationMenu(null);
  createWin();
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWin();
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});

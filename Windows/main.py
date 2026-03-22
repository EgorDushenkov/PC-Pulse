import os
import sys
import socket
import winreg
import threading
import multiprocessing
import customtkinter as ctk
import pystray
import pystray._win32
import pystray._util
import logging
import ctypes


from PIL import Image, ImageDraw
import uvicorn

import server 
kernel32 = ctypes.WinDLL('kernel32')
user32 = ctypes.WinDLL('user32')
hWnd = kernel32.GetConsoleWindow()
if hWnd:
    user32.ShowWindow(hWnd, 0)
ctk.set_appearance_mode("Dark")
ctk.set_default_color_theme("blue")

APP_NAME = "PCPulseServer"

def get_local_ip():
    try:
        import psutil
        addrs = psutil.net_if_addrs()
        for interface_name, interface_addresses in addrs.items():
            for address in interface_addresses:
                if address.family == socket.AF_INET and address.address.startswith("192.168."):
                    return address.address
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except:
        return "127.0.0.1"




def run_fastapi_server():
    log_config = {
        "version": 1,
        "disable_existing_loggers": False,
        "formatters": {},
        "handlers": {},
        "loggers": {
            "uvicorn": {"handlers": [], "level": "CRITICAL"},
            "uvicorn.error": {"handlers": [], "level": "CRITICAL"},
            "uvicorn.access": {"handlers": [], "level": "CRITICAL"},
        },
    }
    
    uvicorn.run(
        server.app, 
        host="0.0.0.0", 
        port=5000, 
        log_config=log_config,
        access_log=False
    )

class PCPulseApp(ctk.CTk):
    def __init__(self):
        super().__init__()

        self.title("PC Pulse Server")
        self.geometry("300x150")
        self.resizable(False, False)
        self.bind("<Unmap>", lambda e: self.hide_window() if self.state() == 'iconic' else None)
        
        self.protocol("WM_DELETE_WINDOW", self.hide_window)

        ip_address = get_local_ip()
        self.label_title = ctk.CTkLabel(self, text="PC Pulse Активен", font=("Roboto", 20, "bold"))
        self.label_title.pack(pady=(20, 5))

        self.label_ip = ctk.CTkLabel(self, text=f"Введите этот IP в приложении:\n{ip_address}", font=("Roboto", 14))
        self.label_ip.pack(pady=(5, 20))

        self.tray_icon = None

    def on_switch_toggle(self):
        toggle_autostart(self.autostart_var.get())


    def hide_window(self):
        self.withdraw()
        image = self.create_tray_image()
        menu = pystray.Menu(
            pystray.MenuItem("Развернуть", self.show_window),
            pystray.MenuItem("Выход", self.quit_app)
        )
        self.tray_icon = pystray.Icon("PCPulse", image, "PC Pulse Server", menu)
        threading.Thread(target=self.tray_icon.run, daemon=True).start()

    def show_window(self, icon, item):
        icon.stop()
        self.after(0, self.deiconify)

    def quit_app(self, icon, item):
        icon.stop()
        self.quit()
        sys.exit()

if __name__ == "__main__":
    multiprocessing.freeze_support()
    if sys.stdout is None:
        sys.stdout = open(os.devnull, "w")
    if sys.stderr is None:
        sys.stderr = open(os.devnull, "w")
    def start_server_process():
        p = multiprocessing.Process(target=run_fastapi_server, daemon=True)
        p.start()
        return p

    server_proc_container = [start_server_process()]

    app = PCPulseApp()

    app.label_ip.configure(text=f"Введите этот IP в приложении:\n{get_local_ip()}")

    app.bind("<Unmap>", lambda e: app.hide_window() if app.state() == 'iconic' else None)

    def check_server():
        if not server_proc_container[0].is_alive():
            server_proc_container[0] = start_server_process()
        app.after(5000, check_server)

    app.after(5000, check_server)
    
    app.mainloop()
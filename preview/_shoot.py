import os, pathlib
from playwright.sync_api import sync_playwright

html = pathlib.Path(__file__).with_name("whale-ui.html").resolve().as_uri()
out = pathlib.Path(__file__).with_name("shots")
out.mkdir(exist_ok=True)

with sync_playwright() as p:
    browser = p.chromium.launch(channel="chrome")
    # Desktop overview at a nice width
    pg = browser.new_page(viewport={"width": 1280, "height": 920}, device_scale_factor=2)
    pg.goto(html)
    pg.wait_for_timeout(600)
    pg.screenshot(path=str(out / "01-overview.png"))
    # Full page home
    pg.screenshot(path=str(out / "02-home.png"), full_page=True)
    # Packs page
    pg.click('[data-go="packs"]'); pg.wait_for_timeout(400)
    pg.screenshot(path=str(out / "03-packs.png"), full_page=True)
    # Settings page
    pg.click('[data-go="settings"]'); pg.wait_for_timeout(400)
    pg.screenshot(path=str(out / "04-settings.png"), full_page=True)
    # Back home, open assistant panel
    pg.click('[data-go="home"]'); pg.wait_for_timeout(300)
    pg.click('#petButton'); pg.wait_for_timeout(400)
    pg.screenshot(path=str(out / "05-panel.png"))
    # Rest flow
    pg.click('#rest'); pg.wait_for_timeout(300)
    pg.screenshot(path=str(out / "06-rest.png"))
    # Mobile width view of home
    m = browser.new_page(viewport={"width": 430, "height": 860}, device_scale_factor=2)
    m.goto(html); m.wait_for_timeout(500)
    m.screenshot(path=str(out / "07-mobile-home.png"), full_page=True)
    browser.close()

print("shots:", sorted(os.listdir(out)))

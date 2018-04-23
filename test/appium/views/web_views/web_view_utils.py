def is_web_view_opened(driver):
    import re
    contexts = driver.contexts
    has_webview = False
    for i in range(0, len(contexts)):
        search = re.search('WEBVIEW', contexts[i])
        if search is not None:
            has_webview = True
    return has_webview
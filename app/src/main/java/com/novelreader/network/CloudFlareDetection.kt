package com.novelreader.network

/**
 * Kotatsu-inspired CF page state probe (manual WebView only — not an auto-solver).
 * Returns: "ok" | "wait" | "error"
 *
 * Detects finished challenge even when cf_clearance cookie barely changes.
 * Includes Indonesian "Tunggu sebentar".
 */
object CloudFlareDetection {
    const val STATE_JS = """
	(function(){
		try {
			var href = (document.location && document.location.href) || '';
			if (href === '' || href === 'about:blank') return 'wait';
			if (document.readyState !== 'interactive' && document.readyState !== 'complete') return 'wait';
			var t = (document.title || '').toLowerCase();
			if (t.indexOf('attention required') !== -1 || t.indexOf('access denied') !== -1) return 'error';
			if (t.indexOf('just a moment') !== -1 || t.indexOf('tunggu sebentar') !== -1 ||
				t.indexOf('un instant') !== -1 || t.indexOf('einen moment') !== -1 ||
				t.indexOf('un momento') !== -1 || t.indexOf('satu saat') !== -1) return 'wait';
			var challengeNodes = document.querySelectorAll(
				'#challenge-running, #challenge-stage, #cf-challenge-running, ' +
				'.cf-browser-verification, #turnstile-wrapper, #cf-please-wait, ' +
				'.cf-challenge-running, #challenge-form'
			);
			for (var i = 0; i < challengeNodes.length; i++) {
				var node = challengeNodes[i];
				var style = window.getComputedStyle(node);
				if (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') continue;
				var rect = node.getBoundingClientRect();
				if (rect.width > 0 && rect.height > 0) return 'wait';
			}
			if (!document.body || document.body.children.length === 0) return 'wait';
			// real site content markers for novel sites
			if (document.body.innerText && document.body.innerText.length < 80) return 'wait';
			return 'ok';
		} catch (e) { return 'wait'; }
	})()
	"""

    fun unwrapJsString(json: String?): String {
        if (json == null || json == "null") return "wait"
        return json.trim().removePrefix("\"").removeSuffix("\"")
    }
}

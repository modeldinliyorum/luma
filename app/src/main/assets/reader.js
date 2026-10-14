// Luma EPUB helpers: highlight re-application and Compatibility Mode detection.
(function () {
    if (window.lumaSetHighlights) return;

    var DEFAULT_COLOR = "rgba(245,197,24,0.400)";

    function parseColor(value) {
        if (!value) return null;
        var match = value.match(/rgba?\(([^)]+)\)/);
        if (!match) return null;
        var parts = match[1].split(",").map(function (p) { return parseFloat(p.trim()); });
        if (parts.length === 4 && parts[3] === 0) return null; // transparent
        if (parts.length >= 3) return [parts[0], parts[1], parts[2]];
        return null;
    }

    function luminance(rgb) {
        return (0.299 * rgb[0] + 0.587 * rgb[1] + 0.114 * rgb[2]) / 255;
    }

    window.lumaSetHighlights = function (marks) {
        try {
            document.querySelectorAll("mark[data-luma]").forEach(function (mark) {
                var parent = mark.parentNode;
                if (!parent) return;
                while (mark.firstChild) parent.insertBefore(mark.firstChild, mark);
                parent.removeChild(mark);
                parent.normalize();
            });

            if (!marks || !marks.length) return;

            var nodes = [];
            var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null);
            while (walker.nextNode()) nodes.push(walker.currentNode);

            marks.forEach(function (item) {
                var text = item && item.t;
                var color = (item && item.c) || DEFAULT_COLOR;
                if (!text) return;
                for (var i = 0; i < nodes.length; i++) {
                    var node = nodes[i];
                    var value = node.nodeValue;
                    if (!value) continue;
                    var index = value.indexOf(text);
                    if (index < 0) continue;
                    var range = document.createRange();
                    range.setStart(node, index);
                    range.setEnd(node, index + text.length);
                    var mark = document.createElement("mark");
                    mark.setAttribute("data-luma", "1");
                    mark.style.background = color;
                    mark.style.color = "inherit";
                    mark.style.borderRadius = "2px";
                    try {
                        range.surroundContents(mark);
                        nodes.splice(i, 1);
                    } catch (e) {
                        // A selection that crosses element boundaries is skipped.
                    }
                    break;
                }
            });
        } catch (e) {
            // Never let highlighting break the reading surface.
        }
    };

    // Inverts the page only when it is dark text on a light background.
    window.lumaApplyCompatibility = function (on) {
        try {
            var root = document.documentElement;
            if (!on) {
                root.classList.remove("luma-compat");
                return;
            }
            var bodyStyle = window.getComputedStyle(document.body);
            var rootStyle = window.getComputedStyle(root);
            var bg = parseColor(bodyStyle.backgroundColor) ||
                parseColor(rootStyle.backgroundColor) ||
                [255, 255, 255];
            var fg = parseColor(bodyStyle.color) || [0, 0, 0];
            if (luminance(bg) >= 0.5 && luminance(fg) < 0.5) {
                root.classList.add("luma-compat");
            } else {
                root.classList.remove("luma-compat");
            }
        } catch (e) {
            // Leave the page untouched if anything goes wrong.
        }
    };
})();

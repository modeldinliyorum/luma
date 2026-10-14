// Luma EPUB highlighter. Re-applies saved highlights by text match.
(function () {
    if (window.lumaSetHighlights) return;

    window.lumaSetHighlights = function (texts) {
        try {
            document.querySelectorAll("mark[data-luma]").forEach(function (mark) {
                var parent = mark.parentNode;
                if (!parent) return;
                while (mark.firstChild) parent.insertBefore(mark.firstChild, mark);
                parent.removeChild(mark);
                parent.normalize();
            });

            if (!texts || !texts.length) return;

            var nodes = [];
            var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null);
            while (walker.nextNode()) nodes.push(walker.currentNode);

            texts.forEach(function (text) {
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
})();

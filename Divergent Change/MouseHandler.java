class MouseHandler extends MouseInputAdapter {

    private final MouseActions mouseActions = new MouseActions("gutter");
    private boolean drag;
    private int toolTipInitialDelay;
    private int toolTipReshowDelay;

    /* ───────── Mouse events ───────── */

    
    public void mouseEntered(MouseEvent e) {
        ToolTipManager ttm = ToolTipManager.sharedInstance();
        toolTipInitialDelay = ttm.getInitialDelay();
        toolTipReshowDelay  = ttm.getReshowDelay();
        ttm.setInitialDelay(0);
        ttm.setReshowDelay(0);
    }

    
    public void mouseExited(MouseEvent e) {
        ToolTipManager ttm = ToolTipManager.sharedInstance();
        ttm.setInitialDelay(toolTipInitialDelay);
        ttm.setReshowDelay(toolTipReshowDelay);
    }

    
    public void mousePressed(MouseEvent e) {
        textArea.requestFocus();

        boolean clickInBorder = e.getX() >= getWidth() - borderWidth * 2;
        if (GUIUtilities.isPopupTrigger(e) || clickInBorder) {
            e.translatePoint(-getWidth(), 0);
            textArea.mouseHandler.mousePressed(e);
            drag = true;
            return;
        }

        Buffer buffer = textArea.getBuffer();
        int lineHeight = textArea.getPainter().getFontMetrics().getHeight();
        int screenLine = e.getY() / lineHeight;
        int line = textArea.chunkCache.getLineInfo(screenLine).physicalLine;
        if (line == -1) return;

        /* --- Determina ação padrão e variante --- */
        String defaultAction, variant;
        if (buffer.isFoldStart(line)) {
            defaultAction = "toggle-fold";
            variant = "fold";
        } else if (structureHighlight &&
                   textArea.isStructureHighlightVisible() &&
                   textArea.lineInStructureScope(line)) {
            defaultAction = "match-struct";
            variant = "struct";
        } else {
            return;
        }

        String action = mouseActions.getActionForEvent(e, variant);
        if (action == null) action = defaultAction;

        /* --- Executa ação --- */
        StructureMatcher.Match match = textArea.getStructureMatch();
        switch (action) {
            case "select-fold" -> {
                textArea.displayManager.expandFold(line, true);
                textArea.selectFold(line);
            }
            case "narrow-fold" -> {
                int[] lines = buffer.getFoldAtLine(line);
                textArea.displayManager.narrow(lines[0], lines[1]);
            }
            case "toggle-fold", "toggle-fold-fully" -> {
                boolean fully = action.endsWith("-fully");
                if (textArea.displayManager.isLineVisible(line + 1)) {
                    textArea.displayManager.collapseFold(line);
                } else {
                    textArea.displayManager.expandFold(line, fully);
                }
            }
            case "match-struct" -> {
                if (match != null) textArea.setCaretPosition(match.end);
            }
            case "select-struct" -> {
                if (match != null) match.matcher.selectMatch(textArea);
            }
            case "narrow-struct" -> {
                if (match != null) {
                    int start = Math.min(match.startLine, textArea.getCaretLine());
                    int end   = Math.max(match.endLine,   textArea.getCaretLine());
                    textArea.displayManager.narrow(start, end);
                }
            }
            default -> { /* nenhuma ação */ }
        }
    }

  
    public void mouseDragged(MouseEvent e) {
        if (drag /* && e.getX() >= getWidth() - borderWidth * 2 */) {
            e.translatePoint(-getWidth(), 0);
            textArea.mouseHandler.mouseDragged(e);
        }
    }


    public void mouseReleased(MouseEvent e) {
        if (drag && e.getX() >= getWidth() - borderWidth * 2) {
            e.translatePoint(-getWidth(), 0);
            textArea.mouseHandler.mouseReleased(e);
        }
        drag = false;
    }
}


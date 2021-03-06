package com.sikulix.editor;

import com.sikulix.core.SX;
import com.sikulix.core.SXLog;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Script implements TableModelListener {

  protected static final SXLog log = SX.getSXLog("SX.SCRIPTEDITOR");
  protected static final int numberCol = 0;
  protected static final int commandCol = 1;
  protected static final int firstParamCol = 2;

  private JFrame window;

  protected JFrame getWindow() {
    return window;
  }

  private Font customFont = new Font("Helvetica Bold", Font.PLAIN, 18);
  PopUpMenus popUpMenus = null;

  ScriptTable table = null;
  public Rectangle rectTable = null;
  int maxCol = 7;

  protected ScriptTable getTable() {
    return table;
  }

  private File fScript = new File(SX.getSXSTORE(), "scripteditor/script.txt");

  protected File getScriptPath() {
    return fScript;
  }

  List<List<ScriptCell>> data = new ArrayList<>();
  List<List<ScriptCell>> savedLine = new ArrayList<>();

  protected List<List<ScriptCell>> getData() {
    return data;
  }

  boolean shouldTrace = false;

  public Script(String[] args) {

    if (args.length > 0 && "trace".equals(args[0])) {
      log.on(SXLog.TRACE);
      shouldTrace = true;
    }

    window = new JFrame("SikuliX - ScriptEditor");
    window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

    JPanel panel = new JPanel(new GridLayout(1, 0));
    panel.setOpaque(true);

    initTemplates();
    loadScript();

    table = new ScriptTable(this, new ScriptTableModel(this, maxCol, data));

    table.setAutoCreateColumnsFromModel(false);

    FontMetrics metrics = table.getFontMetrics(customFont);
    table.setRowHeight(metrics.getHeight() + 4); // set row height to match font

    table.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
    table.setCellSelectionEnabled(true);

    Rectangle monitor = SX.getSXLOCALDEVICE().getMonitor();
    Dimension tableDim = new Dimension((int) (monitor.width * 0.8), (int) (monitor.height * 0.8));
    rectTable = new Rectangle();
    rectTable.setSize(tableDim);
    rectTable.setLocation(tableDim.width / 8, tableDim.height / 9);

    table.setTableHeader(new JTableHeader(table.getColumnModel()) {
      @Override
      public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        d.height = 30;
        return d;
      }
    });

    table.getTableHeader().setBackground(new Color(230, 230, 230));

    int tableW = tableDim.width;
    int tableCols = table.getColumnCount();
    int col0W = 70;
    int col1W = 90;
    int colLast = 400;
    table.getColumnModel().getColumn(0).setPreferredWidth(col0W);
    table.getColumnModel().getColumn(1).setPreferredWidth(col1W);
    table.getColumnModel().getColumn(maxCol).setPreferredWidth(colLast);
    int colsW = (tableW - col0W - col1W - colLast) / (tableCols - 3);
    for (int n = 2; n < tableCols - 1; n++) {
      table.getColumnModel().getColumn(n).setPreferredWidth(colsW);
    }

    table.setGridColor(Color.LIGHT_GRAY);
    table.setShowGrid(false);
    table.setShowHorizontalLines(true);

    table.setPreferredScrollableViewportSize(tableDim);
    table.setFillsViewportHeight(true);

//    table.getModel().addTableModelListener(this);

//    ListSelectionModel listSelectionModel = table.getSelectionModel();
//    listSelectionModel.addListSelectionListener(this);
//    table.setSelectionModel(listSelectionModel);

    table.addMouseListener(new MyMouseAdapter(1, this));
    table.getTableHeader().addMouseListener(new MyMouseAdapter(0, this));
    checkContent();
    collectVariables();

    JScrollPane scrollPane = new JScrollPane(table);
    panel.add(scrollPane);

    window.setContentPane(panel);

    window.pack();
    window.setLocation(rectTable.x, rectTable.y);
    window.setVisible(true);
    table.setSelection(0, 0);

    popUpMenus = new PopUpMenus(this);
  }

//  @Override
//  public void valueChanged(ListSelectionEvent e) {
//    log.trace("valueChanged(ListSelectionEvent)");
//  }

  @Override
  public void tableChanged(TableModelEvent e) {
//    log.trace("tableChanged: ");
  }

  private class MyMouseAdapter extends MouseAdapter {

    private int type = 1;
    private Script script = null;

    private MyMouseAdapter() {
    }

    public MyMouseAdapter(int type, Script script) {
      this.type = type;
      this.script = script;
    }

    public void mouseReleased(MouseEvent me) {
      Point where = me.getPoint();
      int row = table.rowAtPoint(where);
      int col = table.columnAtPoint(where);
      ScriptCell cell = script.cellAt(row, col);
      int button = me.getButton();

      if (row < 0) {
        return;
      }
      int[] selectedRows = table.getSelectedRows();
      if (type > 0) {
        log.trace("clicked: R%d C%d B%d {%d ... %d}",
                row, col, button, selectedRows[0], selectedRows[selectedRows.length - 1]);
      } else {
        log.trace("clicked: Header C%d B%d", col, button);
      }
      if (type > 0 && button > 1) {
        if (selectedRows.length < 2) {
          table.setSelection(row, col);
        }
      }
      if (button == 3) {
        if (type > 0) {
          if (col == 0) {
            popUpMenus.action(cell);
          } else if (col == 1) {
            popUpMenus.command(cell);
          } else {
            popUpMenus.notimplemented(cell);
          }
        } else {
        }
        return;
      }
    }

  }

//  public void tableChanged(TableModelEvent e) {
//      log.trace("tableChanged:");
//  }

  String savedCellText = "";

  protected ScriptCell cellAt(int row, int col) {
    int dataCol = col == 0 ? 0 : col - 1;
    if (row < 0) {
      row = 0;
    }
    int cellRef = tableLines[row];
    if (cellRef != row) {
      row = cellRef;
    }
    if (row > data.size() - 1) {
      log.error("cellAt: row: %d", row);
    }
    if (row > data.size() - 1) {
      data.add(new ArrayList<>());
    }
    List<ScriptCell> line = null;
    try {
      line = data.get(row);
    } catch (IndexOutOfBoundsException ex) {
      log.error("");
    }
    if (dataCol > line.size() - 1) {
      for (int n = line.size(); n <= dataCol; n++) {
        line.add(new ScriptCell(this, "", row, col));
      }
    }
    ScriptCell cell = data.get(row).get(dataCol);
    cell.set(row, col);
    return cell;
  }

  protected void setValueAt(String text, ScriptCell cell) {
    table.getModel().setValueAt(text, cell.getRow(), cell.getCol());
    setSelection(cell);
  }

  protected void setSelection(ScriptCell cell) {
    table.setSelection(cell.getRow(), cell.getCol());
  }

  protected void assist(ScriptCell cell) {
    String cellText = cell.get();
    Script.log.trace("F1: (%d,%d) %s (%d, %d, %d)",
            cell.getRow(), cell.getCol(), cellText,
            cell.getIndent(), cell.getIfIndent(), cell.getLoopIndent());
    if (cellText.startsWith("@")) {
      if (cellText.startsWith("@?")) {
        cell.capture();
      } else {
        cell.show();
      }
    }
  }

  protected void loadScript() {
    data.clear();
    resultsCounter = 0;
    int rowCount = -1;
    String theScript = com.sikulix.core.Content.readFileToString(fScript);
    for (String line : theScript.split("\\n")) {
      List<ScriptCell> aLine = new ArrayList<>();
      int colCount = 1;
      rowCount++;
      for (String cellText : line.split("\\t")) {
        String resultTarget = "$R";
        if (cellText.contains(resultTarget)) {
          int resultCount = -1;
          try {
            resultCount = Integer.parseInt(cellText.replace(resultTarget, "").trim());
          } catch (Exception ex) {
            cellText += "?";
          }
          if (resultCount >= resultsCounter) {
            resultsCounter = resultCount + 1;
          }
        }
        aLine.add(new ScriptCell(this, cellText, rowCount, colCount));
        if (++colCount > maxCol) {
          break;
        }
      }
      if (aLine.size() > 0) {
        data.add(aLine);
      }
    }
    if (SX.isNotNull(table)) {
      checkContent();
      table.setSelection(0, 1);
    }
  }

  protected void saveScript() {
    String theScript = "";
    for (List<ScriptCell> line : data) {
      String sLine = "";
      String sTab = "";
      for (ScriptCell cell : line) {
        sLine += sTab + cell.get();
        sTab = "\t";
      }
      if (SX.isSet(sLine)) {
        theScript += sLine + "\n";
      }
    }
    if (SX.isSet(theScript)) {
      fScript.getParentFile().mkdirs();
      com.sikulix.core.Content.writeStringToFile(theScript, fScript);
    }
  }

  protected void runScript(int lineFrom) {
    runScript(lineFrom, lineFrom);
  }

  protected void runScript(int lineFrom, int lineTo) {
    window.setVisible(false);
    if (lineFrom < 0) {
      lineTo = data.size() - 1;
    }
    for (int n = lineFrom; n <= lineTo; n++) {
      String sLine = "";
      String sep = "";
      for (ScriptCell cell : data.get(n)) {
        sLine += sep + cell.get().trim();
        sep = " | ";
      }
      log.trace("runscript: (%4d) %s", n, sLine);
    }
    //SX.pause(2);
    window.setVisible(true);
  }

  int resultsCounter = 0;

  List<String> variables = new ArrayList<>();

  List<String> images = new ArrayList<>();

  protected void collectVariables() {
    for (List<ScriptCell> line : data) {
      if (line.size() == 0) {
        continue;
      }
      for (ScriptCell cell : line) {
        String cellText = cell.get();
        if (cellText.startsWith("@")) {
          if (!cellText.startsWith("@?")) {
            String imageName = cellText.substring(1);
            if (!images.contains(imageName)) {
              images.add(imageName);
            }
          }
        } else if (cellText.startsWith("$")) {
          String imageName = cellText.substring(1);
          if (!variables.contains(imageName)) {
            variables.add(imageName);
          }
        }
      }
    }
  }

  protected boolean addCommandTemplate(String command, ScriptCell cell) {
    if (cell.isLineEmpty()) {
      command = command.trim();
      String[] commandLine = commandTemplates.get(command);
      if (SX.isNotNull(commandLine)) {
        commandLine = commandLine.clone();
        commandLine[0] = command + commandLine[0];
        int lineLast = commandLine.length - 1;
        String lineEnd = commandLine[lineLast];
        if ("result".equals(lineEnd)) {
          commandLine[lineLast] = "$R" + resultsCounter++;
        }
        cell.setLine(commandLine);
        if (command.startsWith("if") && !command.contains("ifElse")) {
          cell.addLine("endif");
        } else if (command.startsWith("loop")) {
          cell.addLine("endloop");
        } else {
        }
        table.setSelection(cell.getRow(), 2);
      } else {
        cell.setLine(new String[]{command + "?"});
        table.setSelection(cell.getRow(), 1);
      }
      checkContent();
      return true;
    }
    return false;
  }

  protected int[] tableLines;

  protected void checkContent() {
    log.trace("checkContent started");
    int currentIndent = 0;
    int currentIfIndent = 0;
    int currentLoopIndent = 0;
    boolean hasElse = false;
    int nextDataLine = 0;
    tableLines = new int[data.size()];
    for (int ix = 0; ix < tableLines.length; ix++) {
      tableLines[ix] = -1;
    }
    int ixTableLines = -1;
    for (List<ScriptCell> line : data) {
      ScriptCell cell = line.get(0);
      if (!cell.isHiddenBody()) {
        tableLines[++ixTableLines] = nextDataLine;
      }
      if (cell.isFirstHidden()) {
        nextDataLine += cell.getHidden();
      } else if (!cell.isHiddenBody()) {
        nextDataLine++;
      }
      if (line.size() == 0) {
        continue;
      }
      cell.reset();
      String command = cell.get().trim();
      if (SX.isNotNull(commandTemplates.get(command))) {
        if (command.startsWith("if") && !command.contains("ifElse")) {
          currentIfIndent++;
          cell.setIndent(currentIndent, currentIfIndent, -1);
          currentIndent++;
        } else if (command.startsWith("elif")) {
          if (currentIfIndent > 0 && !hasElse) {
            currentIndent--;
            cell.setIndent(currentIndent, currentIfIndent, -1);
            currentIndent++;
          } else {
            cell.setError();
          }
        } else if (command.startsWith("else")) {
          if (currentIfIndent > 0) {
            hasElse = true;
            currentIndent--;
            cell.setIndent(currentIndent, currentIfIndent, -1);
            currentIndent++;
          } else {
            cell.setError();
          }
        } else if (command.startsWith("endif")) {
          if (currentIfIndent > 0) {
            hasElse = false;
            currentIndent--;
            currentIfIndent--;
            cell.setIndent(currentIndent, currentIfIndent, -1);
          } else {
            cell.setError();
          }
        } else if (command.startsWith("loop")) {
          currentLoopIndent++;
          cell.setIndent(currentIndent, -1, currentLoopIndent);
          currentIndent++;
        } else if (command.startsWith("endloop")) {
          if (currentLoopIndent > 0) {
            currentIndent--;
            currentLoopIndent--;
            cell.setIndent(currentIndent, -1, currentLoopIndent);
          } else {
            cell.setError();
          }
        } else {
          cell.setIndent(currentIndent, currentIfIndent, currentLoopIndent);
        }
      }
      if (cell.get().startsWith("@")) {

      }
      if (cell.get().startsWith("$")) {

      }
      //log.trace("checkContent: %s", cell);
    }
    table.tableHasChanged();
    log.trace("checkContent finished");
  }

  Map<String, String[]> commandTemplates = new HashMap<>();

  private void initTemplates() {
    commandTemplates.put("find", new String[]{"", "@?", "result"});
    commandTemplates.put("wait", new String[]{"", "wait-time", "@?", "result"});
    commandTemplates.put("vanish", new String[]{"", "wait-time", "@?", "result"});
    commandTemplates.put("findAll", new String[]{"", "@?", "result"});
    commandTemplates.put("findBest", new String[]{"", "$?listVariable", "result"});
    commandTemplates.put("findAny", new String[]{"", "$?listVariable", "result"});
    commandTemplates.put("click", new String[]{"", "@?", "result"});
    commandTemplates.put("clickRight", new String[]{"", "@?", "result"});
    commandTemplates.put("clickDouble", new String[]{"", "@?", "result"});
    commandTemplates.put("hover", new String[]{"", "@?", "result"});

    commandTemplates.put("if", new String[]{"", "{condition}", "{block}"});
    commandTemplates.put("ifNot", new String[]{"", "{condition}", "{block}"});
    commandTemplates.put("endif", new String[]{""});
    commandTemplates.put("else", new String[]{"", "{block}"});
    commandTemplates.put("elif", new String[]{"", "{condition}", "{block}"});
    commandTemplates.put("elifNot", new String[]{"", "{condition}", "{block}"});
    commandTemplates.put("ifElse", new String[]{"", "{condition}", "{block}", "{block}", "result"});

    commandTemplates.put("loop", new String[]{"", "{condition}", "{block}"});
    commandTemplates.put("loopWith", new String[]{"", "$?listVariable", "{block}"});
    commandTemplates.put("loopFor", new String[]{"", "$?count $?step $?from", "{block}"});
    commandTemplates.put("endloop", new String[]{""});

    commandTemplates.put("print", new String[]{"", "variable..."});
    commandTemplates.put("printf", new String[]{"", "template", "variable..."});
    commandTemplates.put("log", new String[]{"", "template", "variable..."});
    commandTemplates.put("pop", new String[]{"", "message", "result"});

    commandTemplates.put("image", new String[]{"", "@?", "$?similar", "$?offset"});
    commandTemplates.put("variable", new String[]{"", "$V?", "{block}"});
    commandTemplates.put("listVariable", new String[]{"", "$L?", "$?item..."});
    commandTemplates.put("function", new String[]{"", "$F?", "{block}", "$?parameter..."});
    commandTemplates.put("/", new String[]{"continuation"});
    commandTemplates.put("#", new String[]{"comment"});
  }

  protected void editBox(ScriptCell cell) {
    Script.log.trace("EditBox: should open");
  }
}



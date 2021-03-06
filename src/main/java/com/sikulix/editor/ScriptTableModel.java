/*
 * Copyright (c) 2017 - sikulix.com - MIT license
 */

package com.sikulix.editor;

import com.sikulix.core.SX;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

class ScriptTableModel extends AbstractTableModel {

  public ScriptTableModel(Script script, int maxCol, List<List<ScriptCell>> data) {
    this.data = data;
    this.maxCol = maxCol;
    this.script = script;
  }

  Script script = null;
  List<List<ScriptCell>> data = null;
  int maxCol = 1;

  public int getColumnCount() {
    return maxCol + 1;
  }

  public int getRowCount() {
    if (data.size() == 0) {
      data.add(new ArrayList<>());
    }
    return data.size();
  }

  public String getColumnName(int col) {
    if (col == 0) return "    Line";
    if (col == 1) return "Command";
    return String.format("Item%d", col - 1);
  }

  public Object getValueAt(int row, int col) {
    int ixData = script.tableLines[row];
    if (ixData < 0) {
      return "";
    }
    ixData = row;
    ScriptCell commandCell = script.cellAt(ixData, Script.commandCol);
    if (col == Script.numberCol) {
      if (commandCell.isFirstHidden()) {
        return String.format("  V-%s-V ", commandCell.getHidden());
      }
      return String.format("%6d %s", ixData + 1, commandCell.getMarker());
    }
    int lineCol = col - 1;
    List<ScriptCell> line = data.get(ixData);
    if (lineCol > line.size() - 1) {
      return "";
    }
    return data.get(ixData).get(lineCol).get();
  }

  public Class getColumnClass(int c) {
    return String.class;
  }

  public boolean isCellEditable(int row, int col) {
    return true;
  }

  public void setValueAt(Object value, int row, int col) {
    if (row < 0 && col < 0) {
      fireTableDataChanged();
      return;
    }
    String given = ((String) value).trim();
    if (col == 0) {
      return;
    }
    ScriptCell cell = script.cellAt(row, col);
    if (col == 1) {
      if (!given.isEmpty()) {
        if (!cell.isLineEmpty()) {
          cell.set(given);
          script.checkContent();
        } else {
          script.addCommandTemplate(given, cell);
        }
      } else {
        cell.set(given);
        script.checkContent();
        script.setSelection(cell);
      }
    } else {
      cell.set(given);
      fireTableCellUpdated(row, col);
    }
  }

  public void cellUpdated(int row, int col) {
    fireTableCellUpdated(row, col);
  }
}

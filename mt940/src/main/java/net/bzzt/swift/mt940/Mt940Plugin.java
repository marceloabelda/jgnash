/*
 * Copyright (C) 2008 Arnout Engelen
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.bzzt.swift.mt940;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JMenuItem;
import javax.swing.JPanel;

import jgnash.plugin.*;

public class Mt940Plugin implements Plugin {

    @Override
    public void start() {
        // ignored
    }

    @Override
    public void stop() {
        // ignored        
    }

    @Override
    public JMenuItem[] getMenuItems() {

        JMenuItem menuItem = new JMenuItem();

        menuItem.putClientProperty(Plugin.PRECEDINGMENUIDREF, "ofximport-command");

        Logger.getLogger(Mt940Plugin.class.getName()).info("Loading mt940 plugin");

        try {
            menuItem.setAction(new ImportMt940Action());                       
            
            return new JMenuItem[] { menuItem };
        } catch (NoClassDefFoundError e) {
            Logger.getLogger(Mt940Plugin.class.getName()).log(Level.SEVERE, e.getLocalizedMessage(), e);
            return null;
        }
    }

    @Override
    public JPanel getOptionsPanel() {
        return null;
    }

    @Override
    public String getName() {
        return "MT940 Import";
    }

}

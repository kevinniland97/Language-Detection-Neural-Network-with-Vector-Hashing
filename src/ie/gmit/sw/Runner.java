package ie.gmit.sw;

import ie.gmit.sw.gui.Menu;

/**
 * @author Kevin Niland
 * @category GUI
 * @version 1.0
 * 
 *          Runner - Runs the application. Displays the menu as defined in
 *          Menu.java
 */
public class Runner {
	public static void main(String[] args) {
		new Menu().menu();
	}
}
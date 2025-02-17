/*
 * Copyright 2025 Philipp Seerainer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package io.github.seerainer.fileshredder;

import static org.eclipse.swt.events.MenuListener.menuShownAdapter;
import static org.eclipse.swt.events.SelectionListener.widgetSelectedAdapter;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Stack;
import java.util.UUID;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;

/**
 * FileShredder
 *
 * Secure file deletion.
 */
public class FileShredder {

	public static void main(final String[] args) {
		System.setProperty("org.eclipse.swt.display.useSystemTheme", "true");

		final var display = new Display();
		final var shell = new FileShredder(args).open(display);
		while (!shell.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
		display.dispose();
	}

	private static MenuItem menuItem(final Menu parent, final int state, final Menu menu, final SelectionListener listener,
			final int acc, final String text) {
		final var item = new MenuItem(parent, state);

		if (menu != null) {
			item.setMenu(menu);
		}
		if (listener != null) {
			item.addSelectionListener(listener);
		}
		if (acc > 0) {
			item.setAccelerator(acc);
		}
		if (text != null) {
			item.setText(text);
		}

		return item;
	}

	private static Path renameFile(final Path path) throws IOException {
		final var dir = path.getParent().toString();

		var p = path;

		for (var i = 0; i < 25; i++) {
			p = Files.move(p, Path.of(dir + File.separator + UUID.randomUUID()));
		}

		return p;
	}

	private List list;
	private Shell shell;
	private String dir;
	private String[] args;

	private FileShredder(final String[] args) {
		if (args.length > 0) {
			this.args = args;
		}
	}

	private void addItem(final File file) {
		if (file.exists() && file.canRead() && file.canWrite() && file.isFile()) {
			list.add(file.getAbsolutePath());
		}
	}

	private void disableList() {
		if (list.getItemCount() == 0) {
			list.setEnabled(false);
		}
	}

	private Menu menuBar() {
		final var menu = new Menu(shell, SWT.BAR);
		final var file = new Menu(shell, SWT.DROP_DOWN);
		menuItem(menu, SWT.CASCADE, file, null, 0, "&File");
		menuItem(file, SWT.PUSH, null, widgetSelectedAdapter(e -> {
			reset();
			openFiles();
		}), 0, "&Open Files");
		menuItem(file, SWT.PUSH, null, widgetSelectedAdapter(e -> {
			reset();
			openDir();
		}), 0, "Open &Folder (Recursive)");
		menuItem(file, SWT.SEPARATOR, null, null, 0, null);
		final var clear = menuItem(file, SWT.PUSH, null, widgetSelectedAdapter(e -> {
			list.removeAll();
			list.setEnabled(false);
		}), 0, "&Clear List");
		menuItem(file, SWT.SEPARATOR, null, null, 0, null);
		final var shred = menuItem(file, SWT.PUSH, null, null, 0, "&Delete Files");
		menuItem(file, SWT.SEPARATOR, null, null, 0, null);
		menuItem(file, SWT.PUSH, null, widgetSelectedAdapter(e -> shell.close()), SWT.ESC, "E&xit\tEsc");

		final var mode = new Menu(shell, SWT.DROP_DOWN);
		menuItem(menu, SWT.CASCADE, mode, null, 0, "&Options");
		final var zero = menuItem(mode, SWT.CHECK, null, null, 0, "Fill with 0x0 bytes");
		final var maxi = menuItem(mode, SWT.CHECK, null, null, 0, "Fill with 0xFF bytes");
		final var rand = menuItem(mode, SWT.CHECK, null, null, 0, "Fill with random bytes");
		rand.setSelection(true);
		menuItem(mode, SWT.SEPARATOR, null, null, 0, null);
		final var del = menuItem(mode, SWT.CHECK, null, null, 0, "Delete Folder (Open Folder)");
		del.setSelection(true);
		final var ren = menuItem(mode, SWT.CHECK, null, null, 0, "Rename Files (25x times)");
		ren.setSelection(true);

		shred.addSelectionListener(widgetSelectedAdapter(e -> {
			if (shredFiles() && dir != null && del.getSelection()) {
				shredFolder(Path.of(dir).toFile());
			}
		}));

		file.addMenuListener(menuShownAdapter(e -> {
			clear.setEnabled(list.getItemCount() > 0);
			shred.setEnabled(list.getItemCount() > 0 && (zero.getSelection() || maxi.getSelection() || rand.getSelection()));
		}));

		return menu;
	}

	private int message() {
		final var mb = new MessageBox(shell, SWT.ICON_WARNING | SWT.NO | SWT.YES);
		mb.setMessage("Secure delete files?\n\nFiles cannot be recovered!");
		mb.setText("Warning!");
		return mb.open();
	}

	private Shell open(final Display display) {
		final var darkMode = Display.isSystemDarkTheme();
		final var darkBack = new Color(0x30, 0x30, 0x30);
		final var darkFore = new Color(0xDD, 0xDD, 0xDD);

		if ("win32".equals(SWT.getPlatform()) && darkMode) {
			display.setData("org.eclipse.swt.internal.win32.useDarkModeExplorerTheme", Boolean.TRUE);
			display.setData("org.eclipse.swt.internal.win32.useShellTitleColoring", Boolean.TRUE);
			display.setData("org.eclipse.swt.internal.win32.all.use_WS_BORDER", Boolean.TRUE);
			display.setData("org.eclipse.swt.internal.win32.menuBarForegroundColor", darkFore);
			display.setData("org.eclipse.swt.internal.win32.menuBarBackgroundColor", darkBack);
		}

		shell = new Shell(display, SWT.SHELL_TRIM);
		shell.setLayout(new FillLayout());
		shell.setMenuBar(menuBar());
		shell.setText("FileShredder | Secure file deletion");

		list = new List(shell, SWT.BORDER | SWT.SINGLE | SWT.H_SCROLL | SWT.V_SCROLL);
		list.setEnabled(false);

		if (darkMode) {
			list.setBackground(darkBack);
			list.setForeground(darkFore);
		}

		shell.open();

		if (args != null) {
			if (Path.of(args[0]).toFile().isDirectory()) {
				dir = args[0];
				openDir();
			} else {
				openFiles();
			}
		}

		return shell;
	}

	private void openDir() {
		if (dir == null) {
			dir = new DirectoryDialog(shell).open();
		}
		if (dir != null) {
			list.removeAll();
			list.setRedraw(true);

			search().forEach(this::addItem);

			list.setRedraw(true);

			if (list.getItemCount() > 0) {
				list.setEnabled(true);

				if (shredFiles() && shell.getMenuBar().getItem(1).getMenu().getItem(4).getSelection()) {
					shredFolder(Path.of(dir).toFile());
				}
			}
		}

		disableList();
	}

	private void openFiles() {
		if (args == null) {
			final var dialog = new FileDialog(shell, SWT.OPEN | SWT.MULTI);
			dialog.setFilterNames(new String[] { "All Files" });
			dialog.setFilterExtensions(new String[] { "*.*" });

			if (dialog.open() != null) {
				final var files = dialog.getFileNames();
				final var length = files.length;
				args = new String[length];

				for (var i = 0; i < length; i++) {
					args[i] = dialog.getFilterPath() + File.separator + files[i];
				}
			}
		}

		if (args != null) {
			list.removeAll();
			list.setRedraw(false);

			for (final String arg : args) {
				addItem(Path.of(arg).toFile());
			}

			list.setRedraw(true);

			if (list.getItemCount() > 0) {
				list.setEnabled(true);
				shredFiles();
			}
		}

		disableList();
	}

	private void reset() {
		args = null;
		dir = null;
	}

	private java.util.List<File> search() {
		final java.util.List<File> files = new ArrayList<>();
		final var dirs = new Stack<File>();
		final var startdir = new File(dir);

		if (startdir.isDirectory()) {
			dirs.push(startdir);
		}

		while (dirs.size() > 0) {
			for (final var file : dirs.pop().listFiles()) {
				if (file.isDirectory()) {
					dirs.push(file);
				} else if (file.isFile()) {
					files.add(file);
				}
			}
		}

		return files;
	}

	private boolean shredFiles() {
		if (message() == SWT.NO) {
			return false;
		}

		final var secu = new SecureRandom();
		final var menu = shell.getMenuBar().getItem(1).getMenu();
		final var fillWithZeros = menu.getItem(0).getSelection();
		final var fillWithMax = menu.getItem(1).getSelection();
		final var fillWithRandom = menu.getItem(2).getSelection();
		final var renameEnabled = menu.getItem(5).getSelection();

		for (final var item : list.getItems()) {
			var path = Path.of(item);

			try {
				if (renameEnabled) {
					path = renameFile(path);
				}

				final var fileSize = Files.size(path);
				final var buffer = new byte[4096];
				try (var file = new RandomAccessFile(path.toFile(), "rws")) {
					for (var i = 0; i < fileSize; i += buffer.length) {
						if (fillWithZeros) {
							Arrays.fill(buffer, (byte) 0x0);
						} else if (fillWithMax) {
							Arrays.fill(buffer, (byte) 0xFF);
						} else if (fillWithRandom) {
							secu.nextBytes(buffer);
						}

						file.seek(i);
						file.write(buffer, 0, (int) Math.min(buffer.length, fileSize - i));
					}
				}

				Files.delete(path);
			} catch (final Exception e) {
				e.printStackTrace();
			}

			list.remove(item);
		}

		return true;
	}

	private void shredFolder(final File folder) {
		final var files = folder.listFiles();

		if (files != null) {
			for (final var file : files) {
				if (file.isDirectory()) {
					shredFolder(file);
				}
			}
		}

		if (!Path.of(dir).toFile().equals(folder)) {
			folder.delete();
		}
	}
}

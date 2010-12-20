/*******************************************************************************
 * Copyright (c) 2010 Tasktop Technologies and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Tasktop Technologies - initial API and implementation
 *     Itema AS - bug 329897 select event type on open if available
 *     Itema AS - bug 330064 notification filtering and model persistence
 *******************************************************************************/
package org.eclipse.mylyn.internal.commons.ui.notifications;

import java.util.Collection;
import java.util.List;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.CheckStateChangedEvent;
import org.eclipse.jface.viewers.CheckboxTableViewer;
import org.eclipse.jface.viewers.ICheckStateListener;
import org.eclipse.jface.viewers.ICheckStateProvider;
import org.eclipse.jface.viewers.IElementComparer;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.mylyn.commons.ui.notifications.AbstractNotification;
import org.eclipse.mylyn.internal.provisional.commons.ui.CommonImages;
import org.eclipse.mylyn.internal.provisional.commons.ui.SubstringPatternFilter;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.dialogs.FilteredTree;

/**
 * @author Steffen Pingel
 * @author Torkild Ulvøy Resheim
 */
public class NotificationsPreferencesPage extends PreferencePage implements IWorkbenchPreferencePage {

	/**
	 * We need this in order to make sure that the correct element is selected in the {@link TreeViewer} when the
	 * selection is set.
	 * 
	 * @author Torkild Ulvøy Resheim
	 */
	public class NotificationEventComparer implements IElementComparer {

		public boolean equals(Object a, Object b) {
			if (a instanceof NotificationEvent && b instanceof NotificationEvent) {
				String idA = ((NotificationEvent) a).getId();
				String idB = ((NotificationEvent) b).getId();
				return (idA.equals(idB));
			}
			return a.equals(b);
		}

		public int hashCode(Object element) {
			return element.hashCode();
		}

	}

	private static final Object[] EMPTY = new Object[0];

	private final class EventContentProvider implements ITreeContentProvider {

		public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
			// ignore
		}

		public void dispose() {
			// ignore
		}

		public boolean hasChildren(Object element) {
			if (element instanceof NotificationCategory) {
				return ((NotificationCategory) element).getEvents().size() > 0;
			}
			return false;
		}

		public Object getParent(Object element) {
			if (element instanceof NotificationEvent) {
				return ((NotificationEvent) element).getCategory();
			}
			return null;
		}

		public Object[] getElements(Object inputElement) {
			if (inputElement instanceof Object[]) {
				return (Object[]) inputElement;
			} else {
				return EMPTY;
			}
		}

		public Object[] getChildren(Object parentElement) {
			if (parentElement instanceof NotificationCategory) {
				return ((NotificationCategory) parentElement).getEvents().toArray();
			}
			return EMPTY;
		}

	}

	private final class NotifiersContentProvider implements IStructuredContentProvider {

		private NotificationHandler handler;

		public void dispose() {
			// ignore			
		}

		public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
			if (newInput instanceof NotificationHandler) {
				handler = (NotificationHandler) newInput;
			} else {
				handler = null;
			}
		}

		public Object[] getElements(Object inputElement) {
			if (handler != null) {
				return handler.getActions().toArray();
			} else {
				return EMPTY;
			}
		}

	}

	public final class NotificationLabelProvider extends LabelProvider {

		@Override
		public String getText(Object element) {
			if (element instanceof NotificationElement) {
				NotificationElement item = (NotificationElement) element;
				return item.getLabel();
			}
			return super.getText(element);
		}

		@Override
		public Image getImage(Object element) {
			if (element instanceof NotificationElement) {
				NotificationElement item = (NotificationElement) element;
				ImageDescriptor imageDescriptor = item.getImageDescriptor();
				if (imageDescriptor != null) {
					return CommonImages.getImage(imageDescriptor);
				}
			}
			if (element instanceof NotificationEvent) {
				NotificationEvent item = (NotificationEvent) element;
				if (item.isSelected()) {
					return CommonImages.getImage(CommonImages.CHECKED);
				}
			}
			return super.getImage(element);
		}
	}

	private TreeViewer eventsViewer;

	private CheckboxTableViewer notifiersViewer;

	private Button enableNotificationsButton;

	private NotificationModel model;

	private Text descriptionText;

	public NotificationsPreferencesPage() {
	}

	@Override
	public IPreferenceStore getPreferenceStore() {
		return NotificationsPlugin.getDefault().getPreferenceStore();
	}

	@Override
	protected Control createContents(Composite parent) {
		model = NotificationsPlugin.getDefault().getModel();

		Composite composite = new Composite(parent, SWT.NONE);
		composite.setLayout(new GridLayout(2, false));

		enableNotificationsButton = new Button(composite, SWT.CHECK);
		GridDataFactory.fillDefaults().span(2, 1).applyTo(enableNotificationsButton);
		enableNotificationsButton.setText("&Enable notifications");
		enableNotificationsButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				updateEnablement();
			}
		});

		Label label = new Label(composite, SWT.NONE);
		label.setText(" ");
		GridDataFactory.fillDefaults().span(2, 1).applyTo(label);

		label = new Label(composite, SWT.NONE);
		label.setText("Events:");

		label = new Label(composite, SWT.NONE);
		label.setText("Notifiers:");
		// Create the tree showing all the various notification types
		FilteredTree tree = new FilteredTree(composite, SWT.BORDER, new SubstringPatternFilter(), true);
		eventsViewer = tree.getViewer();
		GridDataFactory.fillDefaults().span(1, 2).grab(false, true).applyTo(tree);
		eventsViewer.setComparer(new NotificationEventComparer());
		eventsViewer.setContentProvider(new EventContentProvider());
		eventsViewer.setLabelProvider(new NotificationLabelProvider());
		eventsViewer.setInput(model.getCategories().toArray());
		eventsViewer.expandAll();
		eventsViewer.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				Object input = getDetailsInput((IStructuredSelection) event.getSelection());
				notifiersViewer.setInput(input);

				Object item = ((IStructuredSelection) event.getSelection()).getFirstElement();
				if (item instanceof NotificationEvent) {
					descriptionText.setText(((NotificationEvent) item).getDescription());
					notifiersViewer.getControl().setEnabled(true);
				} else {
					descriptionText.setText(" "); //$NON-NLS-1$
					notifiersViewer.getControl().setEnabled(false);
				}
			}

			private Object getDetailsInput(IStructuredSelection selection) {
				Object item = selection.getFirstElement();
				if (item instanceof NotificationEvent) {
					return model.getOrCreateNotificationHandler((NotificationEvent) item);
				}
				return null;
			}
		});
		// Create the table listing all notification sinks available for the selected event type.
		notifiersViewer = CheckboxTableViewer.newCheckList(composite, SWT.BORDER);
		GridDataFactory.fillDefaults().grab(true, true).applyTo(notifiersViewer.getControl());
		notifiersViewer.setContentProvider(new NotifiersContentProvider());
		notifiersViewer.setLabelProvider(new NotificationLabelProvider());
		notifiersViewer.addCheckStateListener(new ICheckStateListener() {
			public void checkStateChanged(CheckStateChangedEvent event) {
				NotificationAction action = (NotificationAction) event.getElement();
				action.setSelected(event.getChecked());
				model.updateStates();
				model.setDirty(true);
				eventsViewer.refresh();
			}
		});
		notifiersViewer.setCheckStateProvider(new ICheckStateProvider() {
			public boolean isChecked(Object element) {
				return ((NotificationAction) element).isSelected();
			}

			public boolean isGrayed(Object element) {
				return false;
			}
		});
		notifiersViewer.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				Object item = ((IStructuredSelection) event.getSelection()).getFirstElement();
				if (item instanceof NotificationAction) {
					// TODO show configuration pane
				}
			}
		});

		Group group = new Group(composite, SWT.BORDER);
		GridDataFactory.fillDefaults().hint(150, SWT.DEFAULT).grab(true, true).applyTo(group);
		group.setText("Description");
		FillLayout layout = new FillLayout();
		layout.marginHeight = 5;
		layout.marginWidth = 5;
		group.setLayout(layout);

		descriptionText = new Text(group, SWT.WRAP);
		descriptionText.setBackground(group.getBackground());

//		Button testButton = new Button(composite, SWT.NONE);
//		testButton.setText("Test");
//		testButton.addSelectionListener(new SelectionAdapter() {
//			@Override
//			public void widgetSelected(SelectionEvent e) {
//				ISelection selection = eventsViewer.getSelection();
//				if (selection instanceof IStructuredSelection) {
//					Object object = ((IStructuredSelection) selection).getFirstElement();
//					if (object instanceof NotificationEvent) {
//						final NotificationEvent event = (NotificationEvent) object;
//						getControl().getDisplay().asyncExec(new Runnable() {
//							public void run() {
//								Notifications.getService().notify(
//										Collections.singletonList(new TestNotification(event)));
//							}
//						});
//					}
//				}
//			}
//
//		});

		reset();
		Dialog.applyDialogFont(composite);
		return composite;
	}

	class TestNotification extends AbstractNotification {
		private final NotificationEvent event;

		public TestNotification(NotificationEvent event) {
			super(event.getId());
			this.event = event;
		}

		public int compareTo(AbstractNotification arg0) {
			return -1;
		}

		public Object getAdapter(Class adapter) {
			return null;
		}

		@Override
		public void open() {
		}

		@Override
		public String getDescription() {
			return event.getDescription();
		}

		@Override
		public String getLabel() {
			return NLS.bind("Testing {0}", event.getLabel());
		}

		@Override
		public Image getNotificationImage() {
			return null;
		}

		@Override
		public Image getNotificationKindImage() {
			return Dialog.getImage(Dialog.DLG_IMG_MESSAGE_INFO);
		}

	}

	@Override
	public void applyData(Object data) {
		// We may or may not have a NotificationEvent supplied when this 
		// preference dialog is opened. If we do have this data we want to 
		// highlight the appropriate instance.
		if (data instanceof String) {
			String selectedEventId = (String) data;
			Collection<NotificationCategory> items = model.getCategories();
			NotificationEvent selectedEvent = null;
			for (NotificationCategory notificationCategory : items) {
				List<NotificationEvent> event = notificationCategory.getEvents();
				for (NotificationEvent notificationEvent : event) {
					if (notificationEvent.getId().equals(selectedEventId)) {
						selectedEvent = notificationEvent;
						break;
					}
				}
			}
			if (selectedEvent != null) {
				eventsViewer.setSelection(new StructuredSelection(selectedEvent), true);
			}
		}
	}

	private void updateEnablement() {
		boolean enabled = enableNotificationsButton.getSelection();
		eventsViewer.getControl().setEnabled(enabled);
		notifiersViewer.getControl().setEnabled(enabled);// FIXME enabled && notifiersViewer.getInput() != null);
		descriptionText.setEnabled(enabled);
		if (!enabled) {
			eventsViewer.setSelection(StructuredSelection.EMPTY);
		}
		// Update the tree from the model
		Tree tree = eventsViewer.getTree();
		TreeItem[] categories = tree.getItems();
		for (TreeItem category : categories) {
			TreeItem[] events = category.getItems();
			for (TreeItem event : events) {
				NotificationEvent tEvent = (NotificationEvent) event.getData();
				event.setChecked(tEvent.isSelected());
			}
		}
	}

	public void init(IWorkbench workbench) {
		// ignore
	}

	public void reset() {
		enableNotificationsButton.setSelection(getPreferenceStore().getBoolean(
				NotificationsPlugin.PREF_NOTICATIONS_ENABLED));
		updateEnablement();
	}

	@Override
	public boolean performOk() {
		getPreferenceStore().setValue(NotificationsPlugin.PREF_NOTICATIONS_ENABLED,
				enableNotificationsButton.getSelection());
		NotificationsPlugin.getDefault().saveModel();
		return super.performOk();
	}

	@Override
	protected void performDefaults() {
		enableNotificationsButton.setSelection(getPreferenceStore().getDefaultBoolean(
				NotificationsPlugin.PREF_NOTICATIONS_ENABLED));
		updateEnablement();
	}

}

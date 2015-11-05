package org.vaadin.example.ui;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import org.vaadin.example.backend.DatabaseInitialization;
import org.vaadin.example.backend.MessageFacade;
import org.vaadin.example.ui.themes.mytheme.MyTheme;

import com.vaadin.annotations.Theme;
import com.vaadin.cdi.CDIUI;
import com.vaadin.cdi.CDIViewProvider;
import com.vaadin.navigator.Navigator;
import com.vaadin.server.VaadinRequest;
import com.vaadin.ui.Button;
import com.vaadin.ui.Component;
import com.vaadin.ui.UI;

/**
 * UI class using Vaadin CDI addon annotation CDIUI, which makes it a managed
 * bean and allows to inject dependencies to it.
 */
@Theme("mytheme")
@CDIUI("")
public class MyUI extends UI {

    // Stateless EJB providing methods to access Messages
    @Inject
    private MessageFacade messageFacade;

    // CDIViewProvider implements com.vaadin.navigator.ViewProvider and
    // understands container managed views.
    @Inject
    private CDIViewProvider viewProvider;

    private Map<String, Supplier<Long>> badgeSuppliers = new HashMap<>();

    @Override
    protected void init(VaadinRequest vaadinRequest) {
        final ApplicationDesign design = new ApplicationDesign();
        setContent(design);

        Navigator navigator = new Navigator(this, design.messagePanel);
        navigator.addProvider(viewProvider);
        setNavigator(navigator);

        if (navigator.getState().isEmpty()) {
            navigateToFolder(MessageFacade.FOLDER_INBOX);
        }

        setupMenuButtons(design);
    }

    private void navigateToFolder(String folder) {
        getNavigator().navigateTo(FolderView.VIEW_NAME + "/" + folder);
    }

    // Set selected style for menu button when its folder is navigated to
    public void folderSelected(@Observes FolderSelectEvent event) {
        ((ApplicationDesign) getContent()).menuItems.forEach(
                component -> adjustStyleByData(component, event.getFolder()));
    }

    // Adjust all menu badges when a message is modified (marked as read)
    public void messageModified(@Observes MessageModifiedEvent event) {
        ((ApplicationDesign) getContent()).menuItems
                .forEach(this::setMenuBadge);
    }

    private void setupMenuButtons(ApplicationDesign design) {
        mapButton(design.inboxButton, MessageFacade.FOLDER_INBOX,
                Optional.of(() -> messageFacade.countAllUnread()));
        mapButton(design.draftsButton, MessageFacade.FOLDER_DRAFTS);
        mapButton(design.sentButton, MessageFacade.FOLDER_SENT);
        mapButton(design.junkButton, MessageFacade.FOLDER_JUNK);
        mapButton(design.trashButton, MessageFacade.FOLDER_TRASH);
        mapButton(design.flaggedButton, MessageFacade.FOLDER_FLAGGED,
                Optional.of(() -> messageFacade.countFlaggedUnread()));

        design.menuItems.forEach(this::setMenuBadge);
    }

    // Checks if a given Component has a given folder name as data, and adds or
    // removes selected style appropriately
    private void adjustStyleByData(Component component, Object data) {
        if (component instanceof Button) {
            if (data != null && data.equals(((Button) component).getData())) {
                component.addStyleName(MyTheme.SELECTED);
            } else {
                component.removeStyleName(MyTheme.SELECTED);
            }
        }
    }

    // Formats a given Button component's caption to contain HTML in a
    // particular format required by Valo themed menu item component
    private void setMenuBadge(Component component) {
        if (component instanceof Button) {
            Button button = (Button) component;
            if (button.getData() != null
                    && button.getData() instanceof String) {
                String folder = (String) button.getData();
                Long count = badgeSuppliers.containsKey(folder)
                        ? badgeSuppliers.get(folder).get() : 0;
                String badgeText = (count != null && count > 0)
                        ? (count > 99) ? "99+" : count.toString() : "";
                String captionFormat = badgeText.isEmpty() ? ""
                        : "%s <span class=\"valo-menu-badge\">%s</span>";
                if (captionFormat.isEmpty()) {
                    component.setCaption(folder);
                } else {
                    component.setCaption(
                            String.format(captionFormat, folder, badgeText));
                }
            }
        }
    }

    // Map button click to a navigation method.
    // Map button to a folder name to allow setting selected style when folder
    // is navigated without clicking the button.
    // Map button to a Supplier method, that provides count for unread items
    private void mapButton(Button button, String folderName,
            Optional<Supplier<Long>> badgeSupplier) {
        button.addClickListener(event -> navigateToFolder(folderName));
        button.setData(folderName);
        if (badgeSupplier.isPresent()) {
            badgeSuppliers.put(folderName, badgeSupplier.get());
        }
    }

    private void mapButton(Button button, String folderName) {
        mapButton(button, folderName, Optional.empty());
    }

    // Test data initialization is done in contextInitialized Servlet life-cycle
    // event
    @WebListener
    private static class MyUIServlectContextListener
            implements ServletContextListener {

        @Inject
        private DatabaseInitialization init;

        @Override
        public void contextDestroyed(ServletContextEvent arg0) {
            // do nothing
        }

        @Override
        public void contextInitialized(ServletContextEvent arg0) {
            init.initDatabaseIfEmpty();
        }
    }
}

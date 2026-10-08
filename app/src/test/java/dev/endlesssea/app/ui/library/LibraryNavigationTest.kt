package dev.endlesssea.app.ui.library

import org.junit.Assert.*
import org.junit.Test

class LibraryNavigationTest {
    @Test fun defaultsToFavorites() {
        val n = LibraryNavigation.restore()
        assertEquals("FAV", n.destination)
        assertEquals(LibraryArea.COLLECTION, n.area)
        assertFalse(n.onDeviceOnly)
    }
    @Test fun collectionIsRememberedAcrossDownloads() {
        val n = LibraryNavigation.restore().select("ANIME").open(LibraryArea.DOWNLOADS)
        assertEquals("DOWNLOADS", n.destination)
        assertEquals("ANIME", n.open(LibraryArea.COLLECTION).destination)
    }
    @Test fun folderDoesNotBecomeACollectionCategory() {
        val n = LibraryNavigation.restore().select("MOVIE").select("CUSTOM:À revoir")
        assertEquals(LibraryArea.FOLDERS, n.area)
        assertEquals("MOVIE", n.collection)
    }
    @Test fun stableCategoryKeyDoesNotDependOnTabIndex() {
        assertEquals("CUSTOM:À revoir", LibraryNavigation.restore("CUSTOM:À revoir", "ANIME").destination)
    }
    @Test fun downloadsDoNotInheritCollectionAvailabilityFilter() {
        val n = LibraryNavigation.restore().filterDeviceOnly(true).open(LibraryArea.DOWNLOADS)
        assertFalse(n.onDeviceOnly)
        assertTrue(n.open(LibraryArea.COLLECTION).onDeviceOnly)
    }
    @Test fun foldersDoNotInheritCollectionAvailabilityFilter() {
        assertFalse(LibraryNavigation.restore().filterDeviceOnly(true).open(LibraryArea.FOLDERS).onDeviceOnly)
    }
    @Test fun restorationKeepsAreaCollectionAndFilter() {
        val n = LibraryNavigation.restore("DOWNLOADS", "SERIES", true)
        assertEquals(LibraryArea.DOWNLOADS, n.area)
        assertEquals("SERIES", n.open(LibraryArea.COLLECTION).destination)
        assertTrue(n.open(LibraryArea.COLLECTION).onDeviceOnly)
    }
    @Test fun unknownDestinationFallsBackToFavorites() {
        assertEquals("FAV", LibraryNavigation.restore("removed").destination)
    }
    @Test fun blankCustomCategoryIsNotAValidDestination() {
        assertEquals("FAV", LibraryNavigation.restore("CUSTOM:  ").destination)
    }
    @Test fun invalidRememberedCollectionCannotOpenADownloadCategory() {
        assertEquals("FAV", LibraryNavigation.restore("LOCAL", "DOWNLOADS").open(LibraryArea.COLLECTION).destination)
    }
    @Test fun selectedCollectionWinsOverStaleRememberedCollection() {
        assertEquals("ONA", LibraryNavigation.restore("ONA", "FAV").collection)
    }
    @Test fun switchingPrimaryFoldersOpensAllFolders() {
        assertEquals("LOCAL", LibraryNavigation.restore("CUSTOM:Personnel").open(LibraryArea.FOLDERS).destination)
    }
}

/*
 * Copyright (c) 2020 Proton Technologies AG
 * 
 * This file is part of ProtonMail.
 * 
 * ProtonMail is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * ProtonMail is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with ProtonMail. If not, see https://www.gnu.org/licenses/.
 */
package ch.protonmail.android.contacts.list.viewModel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.loader.app.LoaderManager
import ch.protonmail.android.api.ProtonMailApi
import ch.protonmail.android.api.models.room.contacts.ContactsDatabaseFactory
import ch.protonmail.android.contacts.list.listView.ContactItemListFactory
import ch.protonmail.android.contacts.repositories.andorid.baseInfo.AndroidContactsLoaderCallbacksFactory
import ch.protonmail.android.contacts.repositories.andorid.baseInfo.AndroidContactsRepository
import ch.protonmail.android.contacts.repositories.andorid.details.AndroidContactDetailsCallbacksFactory
import ch.protonmail.android.contacts.repositories.andorid.details.AndroidContactDetailsRepository
import ch.protonmail.android.toasts.ToastViewModel
import com.birbit.android.jobqueue.JobManager

/**
 * Created by Kamil Rajtar on 23.08.18.  */
class ContactsListViewModelFactory(private val application: Application, private val loaderManager: LoaderManager,
                                   private val jobManager: JobManager, private val api: ProtonMailApi) : ViewModelProvider.Factory {
	override fun <T : ViewModel?> create(modelClass: Class<T>): T {
		val contactsDatabase = ContactsDatabaseFactory.getInstance(application.applicationContext).getDatabase()
		val contactItemFactory = ContactItemListFactory()
		val androidContactsLoaderCallbacksFactory = AndroidContactsLoaderCallbacksFactory(application.applicationContext, contactItemFactory::convert)
		val androidContactsRepository = AndroidContactsRepository(loaderManager,
				androidContactsLoaderCallbacksFactory)
		val androidContactsDetailsCallbacksFactory = AndroidContactDetailsCallbacksFactory(application.applicationContext)
		val androidContactsDetailsRepository = AndroidContactDetailsRepository(loaderManager, androidContactsDetailsCallbacksFactory)
		val contactListRepository = ContactListRepository(jobManager, api, contactsDatabase)

		val toastsViewModel = ToastViewModel()
		return ContactsListViewModel(
				contactsDatabase, contactListRepository,
				androidContactsRepository, androidContactsDetailsRepository, toastsViewModel) as T
	}
}

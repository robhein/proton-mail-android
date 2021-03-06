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
package ch.protonmail.android.contacts.groups.edit

import android.util.Log
import ch.protonmail.android.api.ProtonMailApi
import ch.protonmail.android.api.models.DatabaseProvider
import ch.protonmail.android.api.models.contacts.receive.ContactLabelFactory
import ch.protonmail.android.api.models.contacts.send.LabelContactsBody
import ch.protonmail.android.api.models.factories.makeInt
import ch.protonmail.android.api.models.room.contacts.*
import ch.protonmail.android.contacts.groups.jobs.CreateContactGroupJob
import ch.protonmail.android.contacts.groups.jobs.RemoveMembersFromContactGroupJob
import ch.protonmail.android.contacts.groups.jobs.SetMembersForContactGroupJob
import com.birbit.android.jobqueue.JobManager
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import java.io.IOException
import javax.inject.Inject

/**
 * Created by kadrikj on 9/5/18. */
class ContactGroupEditCreateRepository @Inject constructor(val jobManager: JobManager, val api: ProtonMailApi, private val databaseProvider: DatabaseProvider) {

    private val contactsDatabase by lazy { /*TODO*/ Log.d("PMTAG", "instantiating contactsDatabase in ContactGroupEditCreateRepository"); databaseProvider.provideContactsDao() }

    fun editContactGroup(contactLabel: ContactLabel): Completable {
        val contactLabelConverterFactory = ContactLabelFactory()
        val labelBody = contactLabelConverterFactory.createServerObjectFromDBObject(contactLabel)
        return api.updateLabelCompletable(contactLabel.ID, labelBody.labelBody)
                .doOnComplete {
                    val joins = contactsDatabase.fetchJoins(contactLabel.ID)
                    contactsDatabase.saveContactGroupLabel(contactLabel)
                    contactsDatabase.saveContactEmailContactLabel(joins)
                }
                .doOnError { throwable ->
                    if (throwable is IOException) {
                        jobManager.addJobInBackground(CreateContactGroupJob(contactLabel.name, contactLabel.color, contactLabel.display,
                                contactLabel.exclusive.makeInt(), true, contactLabel.ID))
                    }
                }
    }

    fun getContactGroupEmails(id: String): Observable<List<ContactEmail>> {
        return contactsDatabase.findAllContactsEmailsByContactGroupAsyncObservable(id)
                .toObservable()
    }

    fun removeMembersFromContactGroup(contactGroupId: String, contactGroupName: String, membersList: List<String>): Completable {
        if (membersList.isEmpty()) {
            return Completable.complete()
        }
        val labelContactsBody = LabelContactsBody(contactGroupId, membersList)
        return api.unlabelContactEmails(labelContactsBody)
                .doOnComplete {
                    val list = ArrayList<ContactEmailContactLabelJoin>()
                    for (contactEmail in membersList) {
                        list.add(ContactEmailContactLabelJoin(contactEmail, contactGroupId))
                    }
                    contactsDatabase.deleteContactEmailContactLabel(list)
                }.doOnError { throwable ->
                    if (throwable is IOException) {
                        jobManager.addJobInBackground(RemoveMembersFromContactGroupJob(contactGroupId, contactGroupName, membersList))
                    }
                }
    }

    fun setMembersForContactGroup(contactGroupId: String, contactGroupName: String, membersList: List<String>): Completable {
        if (membersList.isEmpty()) {
            return Completable.complete()
        }
        val labelContactsBody = LabelContactsBody(contactGroupId, membersList)
        return api.labelContacts(labelContactsBody)
                .doOnComplete {
                    val list = ArrayList<ContactEmailContactLabelJoin>()
                    for (contactEmail in membersList) {
                        list.add(ContactEmailContactLabelJoin(contactEmail, contactGroupId))
                    }
                    getContactGroupEmails(contactGroupId).test().values()
                    contactsDatabase.saveContactEmailContactLabel(list)
                }.doOnError { throwable ->
                    if (throwable is IOException) {
                        jobManager.addJobInBackground(SetMembersForContactGroupJob(contactGroupId, contactGroupName, membersList))
                    }
                }
    }

    fun createContactGroup(contactLabel: ContactLabel): Single<ContactLabel> {
        val contactLabelConverterFactory = ContactLabelFactory()
        val labelBody = contactLabelConverterFactory.createServerObjectFromDBObject(contactLabel)
        return api.createLabelCompletable(labelBody.labelBody)
                .doOnSuccess { label -> contactsDatabase.saveContactGroupLabel(label) }
                .doOnError { throwable ->
                    if (throwable is IOException) {
                        jobManager.addJobInBackground(CreateContactGroupJob(contactLabel.name, contactLabel.color, contactLabel.display,
                                contactLabel.exclusive.makeInt(), false, contactLabel.ID))
                    }
                }
    }
}

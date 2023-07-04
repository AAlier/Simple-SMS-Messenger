package com.simplemobiletools.smsmessenger.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.SHORT_ANIMATION_DURATION
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.adapters.ConversationsAdapter
import com.simplemobiletools.smsmessenger.extensions.config
import com.simplemobiletools.smsmessenger.extensions.conversationsDB
import com.simplemobiletools.smsmessenger.models.Conversation
import kotlinx.android.synthetic.main.activity_main.*

class ArchiveConversationActivity: SimpleActivity() {

    companion object {
        fun create(context: Context) = Intent(context, ArchiveConversationActivity::class.java)
    }

    private val adapter by lazy {
        ConversationsAdapter(
            activity = this,
            recyclerView = conversations_list,
            onRefresh = ::onRefreshAdapter,
            itemClick = ::onItemClicked
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_archive)
        updateMaterialActivityViews(main_coordinator, conversations_list, useTransparentNavigation = true, useTopSearchMenu = true)
        setupList()
        loadCachedConversations()
    }

    private fun setupList() {
        conversations_list.adapter = adapter
        if (areSystemAnimationsEnabled) {
            conversations_list.scheduleLayoutAnimation()
        }
    }

    private fun onRefreshAdapter() {
        adapter.notifyDataSetChanged()
    }

    private fun onItemClicked(conversation: Conversation) {
        startActivity(ThreadActivity.create(this, conversation.threadId, conversation.title))
    }

    private fun loadCachedConversations() {
        ensureBackgroundThread {
            val conversations = conversationsDB.getAllArchived().toMutableList()
            val sortedConversations = conversations.sortedWith(
                compareByDescending<Conversation> {
                    config.pinnedConversations.contains(it.threadId.toString())
                }.thenByDescending { it.date }
            )
            showOrHideProgress(false)
            showOrHidePlaceholder(conversations.isEmpty())

            adapter.updateConversations(sortedConversations) {
                showOrHidePlaceholder(adapter.isEmpty())
            }
        }
    }

    private fun showOrHideProgress(show: Boolean) {
        if (show) {
            conversations_progress_bar.show()
            no_conversations_placeholder.beVisible()
            no_conversations_placeholder.text = getString(R.string.loading_messages)
        } else {
            conversations_progress_bar.hide()
            no_conversations_placeholder.beGone()
        }
    }

    private fun showOrHidePlaceholder(show: Boolean) {
        conversations_fastscroller.beGoneIf(show)
        no_conversations_placeholder.beVisibleIf(show)
        no_conversations_placeholder.text = getString(R.string.no_archived_conversations_found)
        no_conversations_placeholder_2.beVisibleIf(show)
    }

    private fun fadeOutSearch() {
        search_holder.animate().alpha(0f).setDuration(SHORT_ANIMATION_DURATION).withEndAction {
            search_holder.beGone()
//            searchTextChanged("", true)
        }.start()
    }
}

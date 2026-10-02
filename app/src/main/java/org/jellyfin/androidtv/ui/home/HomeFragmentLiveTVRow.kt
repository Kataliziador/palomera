package org.jellyfin.androidtv.ui.home

import android.app.Activity
import android.content.Context
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.constant.LiveTvOption
import org.jellyfin.androidtv.ui.GridButton
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.GridButtonPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.androidtv.util.Utils

class HomeFragmentLiveTVRow(
	private val activity: Activity,
	private val userRepository: UserRepository,
	private val navigationRepository: NavigationRepository,
) : HomeFragmentRow, OnItemViewClickedListener {
	override fun addToRowsAdapter(context: Context, cardPresenter: CardPresenter, rowsAdapter: MutableObjectAdapter<Row>) {
		val header = HeaderItem(rowsAdapter.size().toLong(), activity.getString(R.string.pref_live_tv_cat))
		val adapter = ArrayObjectAdapter(GridButtonPresenter())

		// Solo la entrada a la sección nueva.
		//
		// Los otros tres botones del upstream —grabaciones, programación y
		// series— llevan a la sección LiveTV de Jellyfin, que `CLAUDE.md` §4
		// regla 5 deja **inalcanzable**, y a funciones de DVR que están fuera
		// de alcance (§7). Dejarlos visibles ofrecía al abonado un camino de
		// vuelta a la sección que reemplazamos.
		adapter.add(GridButton(LiveTvOption.LIVE_TV_GUIDE_OPTION_ID, activity.getString(R.string.lbl_live_tv_guide)))

		rowsAdapter.add(ListRow(header, adapter))
	}

	override fun onItemClicked(itemViewHolder: Presenter.ViewHolder?, item: Any?, rowViewHolder: RowPresenter.ViewHolder?, row: Row?) {
		if (item !is GridButton) return

		when (item.id) {
			// Reemplazo de la sección LiveTV de Jellyfin (CLAUDE.md §4, regla 5).
			LiveTvOption.LIVE_TV_GUIDE_OPTION_ID -> navigationRepository.navigate(Destinations.liveTv)
			// Las ramas de grabaciones, programación y series se quedan sin
			// botón que las dispare, pero no se borran: el código viejo
			// permanece en el árbol e inalcanzable, que es lo que pide la
			// regla 5. Borrarlo agrandaría el diff contra upstream sin ganar
			// nada.
			LiveTvOption.LIVE_TV_SCHEDULE_OPTION_ID -> navigationRepository.navigate(Destinations.liveTvSchedule)
			LiveTvOption.LIVE_TV_RECORDINGS_OPTION_ID -> navigationRepository.navigate(Destinations.liveTvRecordings)
			LiveTvOption.LIVE_TV_SERIES_OPTION_ID -> navigationRepository.navigate(Destinations.liveTvSeriesRecordings)
		}
	}
}

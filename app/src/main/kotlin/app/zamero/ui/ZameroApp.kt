package app.zamero.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ZameroApp(vm: ExchangeViewModel = viewModel()) {
    when (val screen = vm.screen) {
        Screen.Form -> ListingFormScreen(
            form = vm.form,
            onChange = vm::update,
            onSearch = vm::search,
        )
        is Screen.Matches -> MatchesScreen(
            myId = ExchangeViewModel.MY_ID,
            chains = screen.chains,
            onBack = vm::backToForm,
        )
    }
}

# Pikaia Android App Architecture

## Overview
Redux-inspired unidirectional data flow with centralized state management and feature-based modularization.

## Core Principles
- **Single Source of Truth**: Global `AppState` managed by `AppStore`
- **Unidirectional Flow**: Actions → Reducers → State → UI
- **Feature Isolation**: Features access store via `FeatureStoreProxy`
- **Declarative UI**: Jetpack Compose

## State Management (`lib/store`)

**AppStore**
- Holds `StateFlow<AppState>` for state observation
- Manages `SharedFlow<NavigationEffect>` for one-time events
- Synchronously applies reducers on dispatch
- Asynchronously executes side effects after state update

**AppState** - Global immutable state container

**Reducer** - Pure function `(Action, AppState) -> AppState`, registered with store

**SideEffect** - Handles async ops (network, DB), dispatches result actions, posts navigation effects

## Feature Structure
```
feature/
├── data/       # Models, repositories
├── domain/     # Use cases
├── redux/      # Actions, State, StoreProxy, Reducer, SideEffects
├── ui/         # Composables, ViewModels
└── di/         # Koin module
```

**FeatureAction** - Sealed interface, all feature actions

**FeatureState** - Feature-specific UI state, projected from AppState

**FeatureStoreProxy** - Bridges AppStore with feature: projects state, filters effects, dispatches actions

**FeatureActionReducer** - Handles feature actions, updates AppState

**FeatureSideEffect** - Triggered on specific actions, executes async work

## ViewModel Pattern

**BaseViewModel<FeatureState, SupportedAction>**
- `viewState: StateFlow<FeatureState>` - UI observes this
- `effect: Flow<NavigationEffect>` - One-time events (navigation, snackbars)
- `handleAction(action)` - UI calls this for user events

Example:
```kotlin
class HomeViewModel(storeProxy: HomeStoreProxy)
    : BaseViewModel<HomeState, HomeAction>(storeProxy, HomeState())
```

## UI Layer

**Two-layer pattern:**
- `FeatureScreen`: ViewModel integration, state/effect observation
- `FeatureScreenContent`: Pure stateless UI

**Navigation Effects** - Observed via `ObserveEffects()`, handles navigation/dialogs

## Dependency Injection (Koin)

**Feature module initialization:**
```kotlin
val featureModule = module {
    single<Repository> { RepositoryImpl() }
    factory { UseCase(get()) }
    single {
        val store: AppStore = get()
        store.addReducer(FeatureReducer())
        store.addSideEffect(FeatureSideEffect(store, get()))
        FeatureStoreProxy(store)
    }
    viewModelOf(::FeatureViewModel)
}
```

**App initialization:** `appModule` → `featureModules` → ViewModels on-demand

## Data Flow Example (Home Feature)

1. `HomeViewModel` receives `HomeStoreProxy`
2. `HomeStoreProxy.getFeatureStateProjection()` maps `AppState.homeMessage` → `HomeState.message`
3. UI collects: `viewModel.viewState.collectAsStateWithLifecycle()`
4. User action: UI calls `viewModel.handleAction(HomeAction.Something)`
5. Action dispatched → Reducer updates AppState → SideEffect executes async work
6. Result action dispatched → State updated → UI recomposes

## Navigation
- Type-safe with `@Serializable` destinations
- Nested graphs (`MainGraph`, etc.)
- Bottom navigation via `BottomNavigationBar`

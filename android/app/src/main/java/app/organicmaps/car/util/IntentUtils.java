package app.organicmaps.car.util;

import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.ScreenManager;

import app.organicmaps.Framework;
import app.organicmaps.Map;
import app.organicmaps.api.Const;
import app.organicmaps.api.ParsedSearchRequest;
import app.organicmaps.api.RequestType;
import app.organicmaps.car.CarAppService;
import app.organicmaps.car.SurfaceRenderer;
import app.organicmaps.car.hacks.PopToRootHack;
import app.organicmaps.car.screens.NavigationScreen;
import app.organicmaps.car.screens.search.SearchScreen;
import app.organicmaps.display.DisplayManager;
import app.organicmaps.display.DisplayType;
import app.organicmaps.routing.RoutingController;
import app.organicmaps.util.log.Logger;

public final class IntentUtils
{
  private static final String TAG = IntentUtils.class.getSimpleName();

  private static final int SEARCH_IN_VIEWPORT_ZOOM = 16;

  public static void processIntent(@NonNull CarContext carContext, @NonNull SurfaceRenderer surfaceRenderer, @NonNull Intent intent)
  {
    final String action = intent.getAction();
    if (CarContext.ACTION_NAVIGATE.equals(action))
      IntentUtils.processNavigationIntent(carContext, surfaceRenderer, intent);
    else if (Intent.ACTION_VIEW.equals(action))
      processViewIntent(carContext, intent);
  }

  // https://developer.android.com/reference/androidx/car/app/CarContext#startCarApp(android.content.Intent)
  private static void processNavigationIntent(@NonNull CarContext carContext, @NonNull SurfaceRenderer surfaceRenderer, @NonNull Intent intent)
  {
    // TODO (AndrewShkrob): This logic will need to be revised when we introduce support for adding stops during navigation or route planning.
    // Skip navigation intents during navigation
    if (RoutingController.get().isNavigating())
      return;

    final Uri uri = intent.getData();
    if (uri == null)
      return;

    final ScreenManager screenManager = carContext.getCarService(ScreenManager.class);
    switch (Framework.nativeParseAndSetApiUrl(uri.toString()))
    {
    case RequestType.INCORRECT:
      return;
    case RequestType.MAP:
      screenManager.popToRoot();
      Map.executeMapApiRequest();
      return;
    case RequestType.SEARCH:
      screenManager.popToRoot();
      final ParsedSearchRequest request = Framework.nativeGetParsedSearchRequest();
      final double[] latlon = Framework.nativeGetParsedCenterLatLon();
      if (latlon != null)
      {
        Framework.nativeStopLocationFollow();
        Framework.nativeSetViewportCenter(latlon[0], latlon[1], SEARCH_IN_VIEWPORT_ZOOM);
        // We need to update viewport for search api manually because of drape engine
        // will not notify subscribers when search activity is shown.
        if (!request.mIsSearchOnMap)
          Framework.nativeSetSearchViewport(latlon[0], latlon[1], SEARCH_IN_VIEWPORT_ZOOM);
      }
      final SearchScreen.Builder builder = new SearchScreen.Builder(carContext, surfaceRenderer);
      builder.setQuery(request.mQuery);
      if (request.mLocale != null)
        builder.setLocale(request.mLocale);

      screenManager.push(new PopToRootHack.Builder(carContext).setScreenToPush(builder.build()).build());
      return;
    case RequestType.ROUTE:
      Logger.e(TAG, "Route API is not supported by Android Auto: " + uri);
      return;
    case RequestType.CROSSHAIR:
      Logger.e(TAG, "Crosshair API is not supported by Android Auto: " + uri);
    }
  }

  private static void processViewIntent(@NonNull CarContext carContext, @NonNull Intent intent)
  {
    final Uri uri = intent.getData();
    if (uri != null
        && Const.API_SCHEME.equals(uri.getScheme())
        && CarAppService.API_CAR_HOST.equals(uri.getSchemeSpecificPart())
        && CarAppService.ACTION_SHOW_NAVIGATION_SCREEN.equals(uri.getFragment()))
    {
      final ScreenManager screenManager = carContext.getCarService(ScreenManager.class);
      final Screen top = screenManager.getTop();
      final DisplayManager displayManager = DisplayManager.from(carContext);
      if (!displayManager.isCarDisplayUsed())
        displayManager.changeDisplay(DisplayType.Car);
      if (!(top instanceof NavigationScreen))
        screenManager.popTo(NavigationScreen.MARKER);
    }
  }

  private IntentUtils() {}
}

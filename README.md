# Supermodel pour Android (expérimental)

Version Android de [Supermodel](https://github.com/trzy/supermodel), l'émulateur
Sega Model 3 de « The Supermodel Team » (GPL 3) : Virtua Fighter 3, Scud
Race, Daytona USA 2, Sega Rally 2, Star Wars Trilogy, etc.

C'est la même chose que la version iOS,
[supermodel-ios](https://github.com/Laurent-67370/supermodel-ios), où Virtua
Fighter 3, Virtua Racing et Sonic Championship tournent à pleine vitesse sur
iPad : le même patch de Supermodel (OpenGL ES 3.0) et la même compilation,
repris de ce dépôt-là. Ici, il n'y a que ce qui est propre à Android :
l'application (Java), sa compilation Gradle et la publication de l'APK.

**État : première version, à essayer.**

## Télécharger

[supermodel.apk](https://github.com/Laurent-67370/supermodel-android/releases/download/supermodel-apk/supermodel.apk)
(publication [`supermodel-apk`](https://github.com/Laurent-67370/supermodel-android/releases/tag/supermodel-apk),
reconstruite à chaque modification de l'app). Android 8 ou plus, ARM64.

## Utilisation

1. Installer `supermodel.apk` (autoriser l'installation depuis cette source),
   lancer l'app et **autoriser l'accès à tous les fichiers** : elle crée le
   dossier **Supermodel** du téléphone (sinon, elle utilise son propre dossier).
2. Mettre les jeux (fichiers `.zip`, les mêmes que pour Supermodel sur
   ordinateur) dans **Supermodel/ROMs**.
3. Relancer l'app : la liste montre les jeux trouvés ; toucher un jeu, puis
   **Load game**.
4. Jouer avec une **manette** : il n'y a pas encore de commandes tactiles.
   Pour revenir à la liste, fermer l'app et la relancer (la mémoire des jeux,
   leurs réglages du menu de service, est enregistrée quand l'app passe en
   arrière-plan).

Les réglages sont dans `Supermodel/Config/Supermodel.ini` et le journal dans
`Supermodel/Supermodel.log` (avec **ShowFrameRate** dans l'onglet Video, le
nombre d'images par seconde y est écrit toutes les 5 secondes).

## Comment c'est fait

- [`supermodel-ios.version`](supermodel-ios.version) : le commit de
  supermodel-ios utilisé (son patch de Supermodel, son `CMakeLists.txt`, et
  `android/android_main.cpp` : le dossier de l'app et la taille du menu).
- [`fetch-supermodel.sh`](fetch-supermodel.sh) : supermodel-ios à ce commit,
  Supermodel patché, SDL2 et SDL2_net.
- [`app/`](app) : l'app ; `SetupActivity` demande l'accès aux fichiers,
  prépare le dossier `Supermodel/` (`Config/`, `ROMs/`, `NVRAM/`, `Saves/`,
  `Assets/`) avec les fichiers de Supermodel, puis `SupermodelActivity` (celle
  de SDL) lance Supermodel (`libmain.so`).
- [`.github/workflows/android.yml`](.github/workflows/android.yml) : l'APK,
  compilé sur une machine Linux de GitHub.

Pour compiler (SDK et NDK d'Android) :

```sh
./fetch-supermodel.sh
./gradlew assembleRelease
```

## Licence

GPL 3 ou ultérieure, comme Supermodel. Aucun jeu ni fichier de Sega n'est
fourni : il faut posséder ses propres jeux.

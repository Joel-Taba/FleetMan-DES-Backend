Liste des bugs monitoring
- les stats publiques ne marchenet pas en prod

Liste des bugs restants (modules auth et account)
    - le register doit permettre uniquement les fleet amangers et les drivers,defautl fleet manager

    - Vérification de la libération véhicule : S'assurer que lors du deleteAccount, l'appel driverUseCase.unassignVehicle met bien à jour la table fleet.vehicles en temps réel.

  
Liste des buggs gestion admins et superadmin
- faire le endpoint pour lister les stast globales et le restaurer vers le controlleur adequat,ne pas oublier la gestion des erreurs ici

-ne plus melanger le global execption handler avec les controlleurs,creer un repertoire pour lui,a coten de dto dans /rest

-le patch et le put en marchenet pas sur les vehicles
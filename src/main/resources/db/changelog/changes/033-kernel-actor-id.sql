-- Référence vers l'acteur Kernel (actor-core) pour chaque conducteur
ALTER TABLE fleet.drivers
    ADD COLUMN IF NOT EXISTS kernel_actor_id UUID;

CREATE UNIQUE INDEX IF NOT EXISTS idx_drivers_kernel_actor
    ON fleet.drivers(kernel_actor_id)
    WHERE kernel_actor_id IS NOT NULL;

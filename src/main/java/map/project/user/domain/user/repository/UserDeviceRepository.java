package map.project.user.domain.user.repository;

import map.project.user.domain.user.entity.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {

    List<UserDevice> findAllByUserId(Long userId);

    boolean existsByDeviceToken(String deviceToken);
}
